package com.campus.contest.module.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyScope;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.organization.dto.SchoolAffiliationPageQuery;
import com.campus.contest.module.organization.dto.SchoolAffiliationReviewRequest;
import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.organization.mapper.SchoolAffiliationMapper;
import com.campus.contest.module.organization.model.EffectiveGrant;
import com.campus.contest.module.organization.model.ScopeType;
import com.campus.contest.module.organization.model.SchoolAffiliationState;
import com.campus.contest.module.organization.model.SchoolSelectionValidation;
import com.campus.contest.module.organization.service.OrganizationScopeService;
import com.campus.contest.module.organization.service.SchoolAffiliationService;
import com.campus.contest.module.organization.vo.SchoolAffiliationItemVO;
import com.campus.contest.module.organization.vo.SchoolAffiliationPageVO;
import com.campus.contest.module.organization.vo.SchoolAffiliationReviewVO;
import com.campus.contest.module.system.dto.AuditLogCommand;
import com.campus.contest.module.system.dto.AuthorizationSnapshot;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.system.service.AuditLogService;
import com.campus.contest.module.system.service.AuthorizationSnapshotService;
import com.campus.contest.module.system.service.DictionaryQueryService;
import com.campus.contest.security.CurrentUserProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchoolAffiliationServiceImpl implements SchoolAffiliationService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int ACTIVE_ORGANIZATION = 1;

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_SCHOOL_ADMIN = "SCHOOL_ADMIN";
    private static final String ROLE_STUDENT = "STUDENT";
    private static final String ROLE_TEACHER = "TEACHER";
    private static final String SCHOOL_TYPE = "SCHOOL";
    private static final String PROVINCE_DICTIONARY = "CN_PROVINCE";

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_VERIFIED = "VERIFIED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final Set<String> STATUSES =
            Set.of(STATUS_PENDING, STATUS_VERIFIED, STATUS_REJECTED);

    private static final String DECISION_APPROVE = "APPROVE";
    private static final String DECISION_REJECT = "REJECT";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String OP_REVIEW = "SCHOOL_AFFILIATION_REVIEW";

    private final SysUserMapper userMapper;
    private final OrganizationMapper organizationMapper;
    private final SchoolAffiliationMapper schoolAffiliationMapper;
    private final OrganizationScopeService organizationScopeService;
    private final AuthorizationSnapshotService authorizationSnapshotService;
    private final DictionaryQueryService dictionaryQueryService;
    private final IdempotencyService idempotencyService;
    private final AuditLogService auditLogService;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public SchoolAffiliationPageVO pageAffiliations(SchoolAffiliationPageQuery query) {
        SchoolAffiliationPageQuery condition =
                query == null ? new SchoolAffiliationPageQuery() : query;
        int page = condition.getPage() == null ? DEFAULT_PAGE : condition.getPage();
        int size = condition.getSize() == null ? DEFAULT_SIZE : condition.getSize();
        if (page < 1) {
            throw invalidParam("page 必须大于等于 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw invalidParam("size 必须为 1-" + MAX_SIZE);
        }
        String status = normalizeStatus(condition.getStatus());
        AffiliationAccess access = currentAccess();

        Long requestedSchoolId = null;
        if (StringUtils.hasText(condition.getSchoolId())) {
            requestedSchoolId = parseId(condition.getSchoolId(), "school_id");
        }
        Set<Long> schoolIds = access.schoolIds();
        if (access.global()) {
            if (requestedSchoolId != null) {
                requireSchoolExists(requestedSchoolId);
                schoolIds = Set.of(requestedSchoolId);
            }
        } else if (requestedSchoolId != null) {
            if (!schoolIds.contains(requestedSchoolId)) {
                throw dataForbidden("无权查看其他学校的归属申请");
            }
            schoolIds = Set.of(requestedSchoolId);
        }

        LambdaQueryWrapper<SysUserDO> wrapper = Wrappers.lambdaQuery();
        wrapper.isNotNull(SysUserDO::getSchoolId)
                .eq(SysUserDO::getSchoolVerificationStatus, status);
        if (!access.global()) {
            wrapper.in(SysUserDO::getSchoolId, schoolIds);
        } else if (requestedSchoolId != null) {
            wrapper.eq(SysUserDO::getSchoolId, requestedSchoolId);
        }
        if (StringUtils.hasText(condition.getKeyword())) {
            String keyword = condition.getKeyword().trim();
            wrapper.and(nested -> nested
                    .like(SysUserDO::getUsername, keyword)
                    .or()
                    .like(SysUserDO::getRealName, keyword)
                    .or()
                    .like(SysUserDO::getStudentNo, keyword));
        }
        wrapper.orderByDesc(SysUserDO::getUpdatedAt)
                .orderByAsc(SysUserDO::getId);

        IPage<SysUserDO> paged = userMapper.selectPage(new Page<>(page, size), wrapper);
        Map<Long, OrganizationDO> schools = loadSchools(paged.getRecords());
        List<SchoolAffiliationItemVO> items = paged.getRecords().stream()
                .map(user -> toItem(user, schools.get(user.getSchoolId())))
                .toList();
        long total = paged.getTotal();
        return new SchoolAffiliationPageVO(
                items,
                total,
                page,
                size,
                (long) page * size < total
        );
    }

    @Override
    public SchoolAffiliationReviewVO reviewAffiliation(
            Long userId,
            SchoolAffiliationReviewRequest request,
            String idempotencyKey
    ) {
        if (userId == null) {
            throw invalidParam("user_id 不能为空");
        }
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        if (request.getVersion() == null || request.getVersion() < 0) {
            throw invalidParam("version 必须为非负整数");
        }
        String decision = request.getDecision() == null
                ? ""
                : request.getDecision().trim().toUpperCase(Locale.ROOT);
        if (!DECISION_APPROVE.equals(decision) && !DECISION_REJECT.equals(decision)) {
            throw invalidParam("decision 只能是 APPROVE 或 REJECT");
        }
        String reason = normalizeReason(request.getReason());
        if (DECISION_REJECT.equals(decision) && reason == null) {
            throw invalidParam("驳回时必须填写 reason");
        }

        Long operatorId = currentUserProvider.requireUserId();
        String key = optionalIdempotencyKey(idempotencyKey);
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("user_id", String.valueOf(userId));
        fingerprint.put("decision", decision);
        fingerprint.put("reason", reason);
        fingerprint.put("version", request.getVersion());

        if (key == null) {
            return doReviewAffiliation(userId, decision, reason, request.getVersion(), operatorId);
        }
        return idempotencyService.execute(
                scopeOf(operatorId),
                OP_REVIEW,
                key,
                fingerprint,
                IDEMPOTENCY_TTL,
                SchoolAffiliationReviewVO.class,
                () -> doReviewAffiliation(
                        userId,
                        decision,
                        reason,
                        request.getVersion(),
                        operatorId
                )
        );
    }

    @Override
    public SchoolAffiliationState getState(Long userId) {
        SysUserDO user = requireUser(userId);
        OrganizationDO school = user.getSchoolId() == null
                ? null
                : organizationMapper.selectById(user.getSchoolId());
        return stateOf(user, school);
    }

    @Override
    public SchoolSelectionValidation validateSelection(
            Long userId,
            Long schoolId,
            Integer version
    ) {
        if (version == null || version < 0) {
            throw invalidParam("version 必须为非负整数");
        }
        SysUserDO user = requireUser(userId);
        if (!Objects.equals(version, user.getVersion())) {
            throw versionConflict();
        }
        AuthorizationSnapshot snapshot = authorizationSnapshotService.loadRequired(userId);
        boolean studentOrTeacher = snapshot.roleCodes().contains(ROLE_STUDENT)
                || snapshot.roleCodes().contains(ROLE_TEACHER);
        if (!studentOrTeacher) {
            throw dataForbidden("只有学生或教师可以选择学校");
        }
        OrganizationDO school = requireSchoolExists(schoolId);
        boolean sameSchool = Objects.equals(user.getSchoolId(), school.getId());
        boolean verified = STATUS_VERIFIED.equals(user.getSchoolVerificationStatus());
        boolean pending = STATUS_PENDING.equals(user.getSchoolVerificationStatus());
        if (sameSchool && (verified || pending)) {
            return new SchoolSelectionValidation(
                    userId,
                    school.getId(),
                    user.getSchoolVerificationStatus(),
                    user.getVersion(),
                    true,
                    false
            );
        }
        if (verified && !sameSchool) {
            throw stateConflict("已确认学校不能由本人直接切换");
        }
        if (pending) {
            throw stateConflict("待审核学校不能直接更换");
        }
        if (!Objects.equals(ACTIVE_ORGANIZATION, school.getStatus())) {
            throw stateConflict("学校已停用，不能选择");
        }
        if (school.getProvinceCode() == null
                || !dictionaryQueryService.containsEnabledValue(
                        PROVINCE_DICTIONARY,
                        school.getProvinceCode()
                )) {
            throw stateConflict("学校所在省份不可用，不能选择");
        }
        return new SchoolSelectionValidation(
                userId,
                school.getId(),
                STATUS_PENDING,
                user.getVersion(),
                false,
                true
        );
    }

    private SchoolAffiliationReviewVO doReviewAffiliation(
            Long userId,
            String decision,
            String reason,
            Integer version,
            Long operatorId
    ) {
        long startedAt = System.currentTimeMillis();
        SchoolAffiliationReviewVO result = transactionTemplate.execute(status -> {
            SysUserDO existing = requireUser(userId);
            if (existing.getSchoolId() == null) {
                throw notFound("用户不存在学校归属申请");
            }
            AffiliationAccess access = accessOf(operatorId);
            if (!access.global() && !access.schoolIds().contains(existing.getSchoolId())) {
                throw dataForbidden("无权审核其他学校的归属申请");
            }
            if (!STATUS_PENDING.equals(existing.getSchoolVerificationStatus())) {
                throw stateConflict("只有 PENDING 学校归属申请可以审核");
            }
            if (!Objects.equals(existing.getVersion(), version)) {
                throw versionConflict();
            }
            if (DECISION_APPROVE.equals(decision)) {
                requireApprovableSchool(existing.getSchoolId());
            }

            String targetStatus = DECISION_APPROVE.equals(decision)
                    ? STATUS_VERIFIED
                    : STATUS_REJECTED;
            LocalDateTime reviewedAt = LocalDateTime.now();
            int rows = schoolAffiliationMapper.updateReview(
                    userId,
                    targetStatus,
                    operatorId,
                    reviewedAt,
                    reason,
                    version
            );
            if (rows != 1) {
                throw versionConflict();
            }
            int newVersion = version + 1;
            Map<String, Object> before = affiliationSnapshot(existing);
            Map<String, Object> after = affiliationSnapshot(existing);
            after.put("status", targetStatus);
            after.put("reviewed_by", String.valueOf(operatorId));
            after.put("reviewed_at", reviewedAt);
            after.put("review_reason", reason);
            after.put("version", newVersion);
            recordAudit(
                    operatorId,
                    userId,
                    Map.of("before", before, "after", after),
                    startedAt
            );
            return new SchoolAffiliationReviewVO(
                    String.valueOf(userId),
                    targetStatus,
                    newVersion
            );
        });
        if (result == null) {
            throw internalError("学校归属审核事务未返回结果");
        }
        return result;
    }

    private AffiliationAccess currentAccess() {
        return accessOf(currentUserProvider.requireUserId());
    }

    private AffiliationAccess accessOf(Long operatorId) {
        List<EffectiveGrant> grants =
                organizationScopeService.loadEffectiveGrants(operatorId);
        boolean global = grants.stream()
                .anyMatch(grant -> ROLE_ADMIN.equals(grant.roleCode())
                        && grant.scopeType() == ScopeType.GLOBAL);
        Set<Long> schoolIds = grants.stream()
                .filter(grant -> ROLE_SCHOOL_ADMIN.equals(grant.roleCode()))
                .filter(grant -> grant.scopeType() == ScopeType.ORGANIZATION)
                .map(EffectiveGrant::scopeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!global && schoolIds.isEmpty()) {
            throw dataForbidden("没有学校归属审核的数据范围");
        }
        return new AffiliationAccess(global, Set.copyOf(schoolIds));
    }

    private Map<Long, OrganizationDO> loadSchools(List<SysUserDO> users) {
        Set<Long> schoolIds = users.stream()
                .map(SysUserDO::getSchoolId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (schoolIds.isEmpty()) {
            return Map.of();
        }
        List<OrganizationDO> organizations = organizationMapper.selectBatchIds(schoolIds);
        return organizations == null
                ? Map.of()
                : organizations.stream().collect(Collectors.toMap(
                        OrganizationDO::getId,
                        organization -> organization,
                        (left, right) -> left
                ));
    }

    private SchoolAffiliationItemVO toItem(SysUserDO user, OrganizationDO school) {
        SchoolAffiliationItemVO item = new SchoolAffiliationItemVO();
        item.setUserId(String.valueOf(user.getId()));
        item.setUsername(user.getUsername());
        item.setRealName(user.getRealName());
        item.setStudentNo(user.getStudentNo());
        item.setSchoolId(String.valueOf(user.getSchoolId()));
        item.setSchoolName(school == null ? "" : school.getOrgName());
        item.setStatus(user.getSchoolVerificationStatus());
        item.setReviewReason(user.getSchoolReviewReason());
        item.setSubmittedAt(user.getUpdatedAt() == null
                ? user.getCreatedAt()
                : user.getUpdatedAt());
        item.setVersion(user.getVersion());
        return item;
    }

    private SchoolAffiliationState stateOf(SysUserDO user, OrganizationDO school) {
        return new SchoolAffiliationState(
                user.getId(),
                user.getSchoolId(),
                school == null ? null : school.getOrgName(),
                school == null ? null : school.getProvinceCode(),
                user.getSchoolVerificationStatus(),
                user.getSchoolReviewReason(),
                user.getSchoolReviewedBy(),
                user.getSchoolReviewedAt(),
                user.getVersion()
        );
    }

    private SysUserDO requireUser(Long userId) {
        SysUserDO user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw notFound("用户不存在");
        }
        return user;
    }

    private OrganizationDO requireSchoolExists(Long schoolId) {
        if (schoolId == null) {
            throw invalidParam("school_id 不能为空");
        }
        OrganizationDO school = organizationMapper.selectById(schoolId);
        if (school == null || !SCHOOL_TYPE.equals(school.getOrgType())) {
            throw notFound("学校不存在");
        }
        return school;
    }

    private void requireApprovableSchool(Long schoolId) {
        OrganizationDO school = schoolId == null
                ? null
                : organizationMapper.selectById(schoolId);
        if (school == null
                || !SCHOOL_TYPE.equals(school.getOrgType())
                || !Objects.equals(ACTIVE_ORGANIZATION, school.getStatus())) {
            throw stateConflict("当前学校已停用、不可用或不是学校组织，不能通过审核");
        }
        if (school.getProvinceCode() == null
                || !dictionaryQueryService.containsEnabledValue(
                        PROVINCE_DICTIONARY,
                        school.getProvinceCode()
                )) {
            throw stateConflict("当前学校所在省份不可用，不能通过审核");
        }
    }

    private String normalizeStatus(String value) {
        String status = StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : STATUS_PENDING;
        if (!STATUSES.contains(status)) {
            throw invalidParam("status 只能是 PENDING、VERIFIED 或 REJECTED");
        }
        return status;
    }

    private String normalizeReason(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String reason = value.trim();
        if (reason.length() > 500) {
            throw invalidParam("reason 长度不能超过 500");
        }
        return reason;
    }

    private Long parseId(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw invalidParam(field + " 不能为空");
        }
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) {
                throw invalidParam(field + " 必须为正整数");
            }
            return id;
        } catch (NumberFormatException e) {
            throw invalidParam(field + " 必须为数字 ID");
        }
    }

    private Map<String, Object> affiliationSnapshot(SysUserDO user) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("user_id", String.valueOf(user.getId()));
        snapshot.put("school_id", user.getSchoolId() == null
                ? null
                : String.valueOf(user.getSchoolId()));
        snapshot.put("status", user.getSchoolVerificationStatus());
        snapshot.put("reviewed_by", user.getSchoolReviewedBy() == null
                ? null
                : String.valueOf(user.getSchoolReviewedBy()));
        snapshot.put("reviewed_at", user.getSchoolReviewedAt());
        snapshot.put("review_reason", user.getSchoolReviewReason());
        snapshot.put("version", user.getVersion());
        return snapshot;
    }

    private void recordAudit(
            Long operatorId,
            Long userId,
            Map<String, Object> params,
            long startedAt
    ) {
        HttpServletRequest request = currentRequest();
        auditLogService.record(new AuditLogCommand(
                operatorId,
                "ORGANIZATION",
                OP_REVIEW,
                request == null ? null : request.getRequestURI(),
                request == null ? null : request.getMethod(),
                params(Map.of("target_user_id", String.valueOf(userId), "change", params)),
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

    private BizException notFound(String message) {
        return new BizException(ErrorCode.RESOURCE_NOT_FOUND.getCode(), message);
    }

    private BizException dataForbidden(String message) {
        return new BizException(ErrorCode.DATA_FORBIDDEN.getCode(), message);
    }

    private BizException stateConflict(String message) {
        return new BizException(ErrorCode.BUSINESS_CONFLICT.getCode(), message);
    }

    private BizException versionConflict() {
        return new BizException(
                ErrorCode.BUSINESS_CONFLICT.getCode(),
                "学校归属已被修改，请刷新后重试"
        );
    }

    private BizException invalidParam(String message) {
        return new BizException(ErrorCode.INVALID_PARAM.getCode(), message);
    }

    private BizException internalError(String message) {
        return new BizException(ErrorCode.INTERNAL_ERROR.getCode(), message);
    }

    private record AffiliationAccess(
            boolean global,
            Set<Long> schoolIds
    ) {
    }
}
