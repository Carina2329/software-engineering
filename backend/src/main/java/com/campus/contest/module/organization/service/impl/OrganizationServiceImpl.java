package com.campus.contest.module.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyScope;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.organization.dto.OrganizationCreateRequest;
import com.campus.contest.module.organization.dto.OrganizationPageQuery;
import com.campus.contest.module.organization.dto.OrganizationUpdateRequest;
import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.organization.model.EffectiveScope;
import com.campus.contest.module.organization.model.OrganizationType;
import com.campus.contest.module.organization.service.OrganizationScopeService;
import com.campus.contest.module.organization.service.OrganizationService;
import com.campus.contest.module.organization.vo.OrganizationCreatedVO;
import com.campus.contest.module.organization.vo.OrganizationItemVO;
import com.campus.contest.module.organization.vo.OrganizationPageVO;
import com.campus.contest.module.system.dto.AuditLogCommand;
import com.campus.contest.module.system.service.AuditLogService;
import com.campus.contest.module.system.service.DictionaryQueryService;
import com.campus.contest.module.system.vo.IdVersionVO;
import com.campus.contest.security.CurrentUserProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int ORGANIZATION_ACTIVE = 1;
    private static final int ORGANIZATION_DISABLED = 0;
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String PROVINCE_DICTIONARY = "CN_PROVINCE";

    private static final String OP_CREATE = "ORGANIZATION_CREATE";
    private static final String OP_UPDATE = "ORGANIZATION_UPDATE";

    private final OrganizationMapper organizationMapper;
    private final OrganizationScopeService organizationScopeService;
    private final DictionaryQueryService dictionaryQueryService;
    private final IdempotencyService idempotencyService;
    private final AuditLogService auditLogService;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public OrganizationPageVO pageOrganizations(OrganizationPageQuery query) {
        OrganizationPageQuery condition = query == null ? new OrganizationPageQuery() : query;
        int page = condition.getPage() == null ? DEFAULT_PAGE : condition.getPage();
        int size = condition.getSize() == null ? DEFAULT_SIZE : condition.getSize();
        if (page < 1) {
            throw invalidParam("page 必须大于等于 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw invalidParam("size 必须为 1-" + MAX_SIZE);
        }

        EffectiveScope scope = organizationScopeService.loadCurrentRequired();
        if (!scope.global() && scope.organizationIds().isEmpty()) {
            return OrganizationPageVO.empty(page, size);
        }

        OrganizationType type = null;
        if (StringUtils.hasText(condition.getOrgType())) {
            type = parseOrganizationType(condition.getOrgType());
        }
        String provinceCode = null;
        if (StringUtils.hasText(condition.getProvinceCode())) {
            provinceCode = normalizeProvinceCode(condition.getProvinceCode());
            requireProvinceExists(provinceCode);
        }

        LambdaQueryWrapper<OrganizationDO> wrapper = Wrappers.lambdaQuery();
        if (!scope.global()) {
            wrapper.in(OrganizationDO::getId, scope.organizationIds());
        }
        if (type != null) {
            wrapper.eq(OrganizationDO::getOrgType, type.name());
        }
        if (provinceCode != null) {
            wrapper.eq(OrganizationDO::getProvinceCode, provinceCode);
        }
        if (StringUtils.hasText(condition.getKeyword())) {
            String keyword = condition.getKeyword().trim();
            wrapper.and(nested -> nested
                    .like(OrganizationDO::getOrgCode, keyword)
                    .or()
                    .like(OrganizationDO::getOrgName, keyword));
        }
        wrapper.orderByAsc(OrganizationDO::getId);

        IPage<OrganizationDO> paged = organizationMapper.selectPage(
                new Page<>(page, size),
                wrapper
        );
        List<OrganizationItemVO> items = paged.getRecords().stream()
                .map(OrganizationItemVO::from)
                .toList();
        long total = paged.getTotal();
        return new OrganizationPageVO(
                items,
                total,
                page,
                size,
                (long) page * size < total
        );
    }

    @Override
    public OrganizationCreatedVO createOrganization(
            OrganizationCreateRequest request,
            String idempotencyKey
    ) {
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        String orgCode = normalizeOrgCode(request.getOrgCode());
        String orgName = normalizeOrgName(request.getOrgName());
        OrganizationType type = parseOrganizationType(request.getOrgType());
        String provinceCode = normalizeProvinceForType(type, request.getProvinceCode(), true);
        String key = requireIdempotencyKey(idempotencyKey);
        Long operatorId = currentUserProvider.requireUserId();
        requireGlobalScope();

        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("org_code", orgCode);
        fingerprint.put("org_name", orgName);
        fingerprint.put("org_type", type.name());
        fingerprint.put("province_code", provinceCode);

        return idempotencyService.execute(
                scopeOf(operatorId),
                OP_CREATE,
                key,
                fingerprint,
                IDEMPOTENCY_TTL,
                OrganizationCreatedVO.class,
                () -> doCreateOrganization(
                        orgCode,
                        orgName,
                        type,
                        provinceCode,
                        operatorId
                )
        );
    }

    @Override
    public OrganizationItemVO getOrganization(Long organizationId) {
        EffectiveScope scope = organizationScopeService.loadCurrentRequired();
        OrganizationDO organization = organizationId == null
                ? null
                : organizationMapper.selectById(organizationId);
        if (organization == null || !canAccessForOrganizationEndpoints(scope, organizationId)) {
            throw notFound("组织不存在或对当前用户不可见");
        }
        return OrganizationItemVO.from(organization);
    }

    @Override
    public IdVersionVO updateOrganization(
            Long organizationId,
            OrganizationUpdateRequest request,
            String idempotencyKey
    ) {
        if (organizationId == null) {
            throw invalidParam("organization_id 不能为空");
        }
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        if (request.getVersion() == null || request.getVersion() < 0) {
            throw invalidParam("version 必须为非负整数");
        }
        if (!request.hasOrgName() && !request.hasProvinceCode() && request.getStatus() == null) {
            throw invalidParam("org_name、province_code、status 至少提交一项");
        }
        String key = optionalIdempotencyKey(idempotencyKey);
        Long operatorId = currentUserProvider.requireUserId();
        EffectiveScope scope = organizationScopeService.loadCurrentRequired();
        OrganizationDO existing = requireOrganization(organizationId);
        if (!canAccessForOrganizationEndpoints(scope, organizationId)) {
            throw dataForbidden("无权管理该组织");
        }

        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("organization_id", String.valueOf(organizationId));
        fingerprint.put("org_name_present", request.hasOrgName());
        fingerprint.put("org_name", request.hasOrgName() ? request.getOrgName() : null);
        fingerprint.put("province_code_present", request.hasProvinceCode());
        fingerprint.put("province_code", request.hasProvinceCode() ? request.getProvinceCode() : null);
        fingerprint.put("status", request.getStatus());
        fingerprint.put("version", request.getVersion());

        if (key == null) {
            return doUpdateOrganization(organizationId, request, operatorId);
        }
        return idempotencyService.execute(
                scopeOf(operatorId),
                OP_UPDATE,
                key,
                fingerprint,
                IDEMPOTENCY_TTL,
                IdVersionVO.class,
                () -> doUpdateOrganization(organizationId, request, operatorId)
        );
    }

    private OrganizationCreatedVO doCreateOrganization(
            String orgCode,
            String orgName,
            OrganizationType type,
            String provinceCode,
            Long operatorId
    ) {
        long startedAt = System.currentTimeMillis();
        OrganizationCreatedVO result = transactionTemplate.execute(status -> {
            long duplicates = organizationMapper.selectCount(
                    Wrappers.<OrganizationDO>lambdaQuery()
                            .eq(OrganizationDO::getOrgCode, orgCode)
            );
            if (duplicates > 0) {
                throw duplicateOrgCode();
            }

            LocalDateTime now = LocalDateTime.now();
            OrganizationDO entity = new OrganizationDO();
            entity.setOrgCode(orgCode);
            entity.setOrgName(orgName);
            entity.setOrgType(type.name());
            entity.setProvinceCode(provinceCode);
            entity.setStatus(ORGANIZATION_ACTIVE);
            entity.setCreatedAt(now);
            entity.setCreatedBy(operatorId);
            entity.setUpdatedAt(now);
            entity.setUpdatedBy(operatorId);
            entity.setDeleted(0);
            entity.setVersion(0);
            try {
                organizationMapper.insert(entity);
            } catch (DuplicateKeyException e) {
                throw duplicateOrgCode();
            }

            Map<String, Object> after = organizationSnapshot(entity);
            recordAudit(
                    operatorId,
                    OP_CREATE,
                    Map.of("after", after),
                    startedAt
            );
            return new OrganizationCreatedVO(
                    String.valueOf(entity.getId()),
                    entity.getOrgCode()
            );
        });
        if (result == null) {
            throw internalError("组织创建事务未返回结果");
        }
        return result;
    }

    private IdVersionVO doUpdateOrganization(
            Long organizationId,
            OrganizationUpdateRequest request,
            Long operatorId
    ) {
        long startedAt = System.currentTimeMillis();
        IdVersionVO result = transactionTemplate.execute(status -> {
            OrganizationDO existing = requireOrganization(organizationId);
            if (!Objects.equals(existing.getVersion(), request.getVersion())) {
                throw versionConflict();
            }

            OrganizationType type = OrganizationType.from(existing.getOrgType())
                    .orElseThrow(() -> internalError("数据库中的组织类型非法"));
            String effectiveName = request.hasOrgName()
                    ? normalizeOrgName(request.getOrgName())
                    : existing.getOrgName();
            String effectiveProvince = request.hasProvinceCode()
                    ? normalizeProvinceForType(type, request.getProvinceCode(), false)
                    : existing.getProvinceCode();
            Integer effectiveStatus = request.getStatus() == null
                    ? existing.getStatus()
                    : normalizeStatus(request.getStatus());

            OrganizationDO update = new OrganizationDO();
            update.setId(organizationId);
            update.setOrgName(effectiveName);
            update.setProvinceCode(effectiveProvince);
            update.setStatus(effectiveStatus);
            update.setUpdatedAt(LocalDateTime.now());
            update.setUpdatedBy(operatorId);
            update.setVersion(request.getVersion());
            int rows = organizationMapper.updateById(update);
            if (rows != 1) {
                throw versionConflict();
            }

            int newVersion = request.getVersion() + 1;
            Map<String, Object> after = organizationSnapshot(existing);
            after.put("org_name", effectiveName);
            after.put("province_code", effectiveProvince);
            after.put("status", effectiveStatus);
            after.put("version", newVersion);
            recordAudit(
                    operatorId,
                    OP_UPDATE,
                    Map.of(
                            "before", organizationSnapshot(existing),
                            "after", after
                    ),
                    startedAt
            );
            return new IdVersionVO(String.valueOf(organizationId), newVersion);
        });
        if (result == null) {
            throw internalError("组织修改事务未返回结果");
        }
        return result;
    }

    private OrganizationDO requireOrganization(Long organizationId) {
        OrganizationDO organization = organizationId == null
                ? null
                : organizationMapper.selectById(organizationId);
        if (organization == null) {
            throw notFound("组织不存在或不可见");
        }
        return organization;
    }

    private boolean canAccessForOrganizationEndpoints(
            EffectiveScope scope,
            Long organizationId
    ) {
        return scope.global() || scope.organizationIds().contains(organizationId);
    }

    private void requireGlobalScope() {
        if (!organizationScopeService.loadCurrentRequired().global()) {
            throw dataForbidden("仅平台管理员可创建组织");
        }
    }

    private String normalizeOrgCode(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 32) {
            throw invalidParam("org_code 长度必须为 1-32");
        }
        return normalized;
    }

    private String normalizeOrgName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 128) {
            throw invalidParam("org_name 长度必须为 1-128");
        }
        return normalized;
    }

    private OrganizationType parseOrganizationType(String value) {
        return OrganizationType.from(value)
                .orElseThrow(() -> invalidParam(
                        "org_type 只能是 SCHOOL、PROVINCE_ORGANIZER、NATIONAL_ORGANIZER、PLATFORM"
                ));
    }

    private String normalizeProvinceForType(
            OrganizationType type,
            String value,
            boolean creating
    ) {
        String provinceCode = normalizeProvinceCodeNullable(value);
        if (type.provinceRequired() && provinceCode == null) {
            throw invalidParam("学校或省级主办方必须填写 province_code");
        }
        if (!type.provinceRequired() && provinceCode != null) {
            throw invalidParam("国家级和平台组织不得填写 province_code");
        }
        if (provinceCode != null) {
            requireProvinceExists(provinceCode);
        } else if (creating && type.provinceRequired()) {
            throw invalidParam("province_code 不能为空");
        }
        return provinceCode;
    }

    private String normalizeProvinceCode(String value) {
        String provinceCode = normalizeProvinceCodeNullable(value);
        if (provinceCode == null) {
            throw invalidParam("province_code 不能为空");
        }
        return provinceCode;
    }

    private String normalizeProvinceCodeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void requireProvinceExists(String provinceCode) {
        if (!dictionaryQueryService.containsEnabledValue(PROVINCE_DICTIONARY, provinceCode)) {
            throw invalidParam("province_code 不在启用的 CN_PROVINCE 字典中");
        }
    }

    private Integer normalizeStatus(Integer status) {
        if (!Objects.equals(ORGANIZATION_ACTIVE, status)
                && !Objects.equals(ORGANIZATION_DISABLED, status)) {
            throw invalidParam("status 只能是 0 或 1");
        }
        return status;
    }

    private String requireIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw invalidParam("缺少 Idempotency-Key 请求头");
        }
        String key = idempotencyKey.trim();
        if (key.length() > 64) {
            throw invalidParam("Idempotency-Key 必须为 1-64 位非空字符串");
        }
        return key;
    }

    private String optionalIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        String key = idempotencyKey.trim();
        if (key.length() > 64) {
            throw invalidParam("Idempotency-Key 必须为 1-64 位非空字符串");
        }
        return key;
    }

    private IdempotencyScope scopeOf(Long operatorId) {
        return new IdempotencyScope(
                IdempotencyScope.PrincipalType.USER,
                String.valueOf(operatorId)
        );
    }

    private Map<String, Object> organizationSnapshot(OrganizationDO organization) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("organization_id", organization.getId() == null
                ? null
                : String.valueOf(organization.getId()));
        snapshot.put("org_code", organization.getOrgCode());
        snapshot.put("org_name", organization.getOrgName());
        snapshot.put("org_type", organization.getOrgType());
        snapshot.put("province_code", organization.getProvinceCode());
        snapshot.put("status", organization.getStatus());
        snapshot.put("version", organization.getVersion());
        return snapshot;
    }

    private void recordAudit(
            Long operatorId,
            String operation,
            Map<String, Object> params,
            long startedAt
    ) {
        HttpServletRequest request = currentRequest();
        auditLogService.record(new AuditLogCommand(
                operatorId,
                "ORGANIZATION",
                operation,
                request == null ? null : request.getRequestURI(),
                request == null ? null : request.getMethod(),
                params(params),
                request == null ? null : request.getRemoteAddr(),
                Math.toIntExact(System.currentTimeMillis() - startedAt),
                0,
                null
        ));
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String params(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw internalError("审计参数序列化失败");
        }
    }

    private BizException duplicateOrgCode() {
        return new BizException(
                ErrorCode.DUPLICATE_RELATION.getCode(),
                "组织编码已存在"
        );
    }

    private BizException versionConflict() {
        return new BizException(
                ErrorCode.BUSINESS_CONFLICT.getCode(),
                "组织已被他人修改，请刷新后重试"
        );
    }

    private BizException notFound(String message) {
        return new BizException(ErrorCode.RESOURCE_NOT_FOUND.getCode(), message);
    }

    private BizException dataForbidden(String message) {
        return new BizException(ErrorCode.DATA_FORBIDDEN.getCode(), message);
    }

    private BizException invalidParam(String message) {
        return new BizException(ErrorCode.INVALID_PARAM.getCode(), message);
    }

    private BizException internalError(String message) {
        return new BizException(ErrorCode.INTERNAL_ERROR.getCode(), message);
    }
}
