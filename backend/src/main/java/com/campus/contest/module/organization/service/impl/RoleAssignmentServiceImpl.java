package com.campus.contest.module.organization.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyScope;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.organization.dto.RoleAssignmentCreateRequest;
import com.campus.contest.module.organization.dto.RoleAssignmentUpdateRequest;
import com.campus.contest.module.organization.entity.ContestEditionScopeDO;
import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.ContestEditionScopeMapper;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.organization.mapper.RoleAssignmentMapper;
import com.campus.contest.module.organization.model.EffectiveGrant;
import com.campus.contest.module.organization.model.ScopeType;
import com.campus.contest.module.organization.service.OrganizationScopeService;
import com.campus.contest.module.organization.service.RoleAssignmentService;
import com.campus.contest.module.system.dto.AuditLogCommand;
import com.campus.contest.module.system.entity.SysRoleDO;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.entity.SysUserRoleDO;
import com.campus.contest.module.system.mapper.SysRoleMapper;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.system.service.AuditLogService;
import com.campus.contest.module.system.service.DictionaryQueryService;
import com.campus.contest.module.system.service.RbacSideEffectService;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleAssignmentServiceImpl implements RoleAssignmentService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_SCHOOL_ADMIN = "SCHOOL_ADMIN";
    private static final String ROLE_PROVINCE_ADMIN = "PROVINCE_ADMIN";
    private static final String ROLE_NATIONAL_ADMIN = "NATIONAL_ADMIN";
    private static final String ROLE_EVENT_MANAGER = "EVENT_MANAGER";
    private static final String ROLE_REVIEWER = "REVIEWER";

    private static final String ORG_SCHOOL = "SCHOOL";
    private static final String ORG_NATIONAL = "NATIONAL_ORGANIZER";
    private static final String PROVINCE_DICTIONARY = "CN_PROVINCE";

    private static final int USER_ACTIVE = 1;
    private static final int GRANT_ACTIVE = 1;
    private static final int GRANT_INACTIVE = 0;
    private static final int NOT_DELETED = 0;
    private static final int ORGANIZATION_ACTIVE = 1;
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private static final String OP_CREATE = "ROLE_ASSIGNMENT_CREATE";
    private static final String OP_UPDATE = "ROLE_ASSIGNMENT_UPDATE";

    private static final Set<String> SUPPORTED_ROLES = Set.of(
            ROLE_SCHOOL_ADMIN,
            ROLE_PROVINCE_ADMIN,
            ROLE_NATIONAL_ADMIN,
            ROLE_EVENT_MANAGER,
            ROLE_REVIEWER
    );
    private static final Set<String> DELEGABLE_ROLES = Set.of(
            ROLE_EVENT_MANAGER,
            ROLE_REVIEWER
    );

    private final RoleAssignmentMapper roleAssignmentMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final OrganizationMapper organizationMapper;
    private final ContestEditionScopeMapper contestEditionMapper;
    private final DictionaryQueryService dictionaryQueryService;
    private final OrganizationScopeService organizationScopeService;
    private final IdempotencyService idempotencyService;
    private final AuditLogService auditLogService;
    private final RbacSideEffectService sideEffectService;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public IdVersionVO createAssignment(
            RoleAssignmentCreateRequest request,
            String idempotencyKey
    ) {
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        Long targetUserId = parseId(request.getUserId(), "user_id");
        String roleCode = normalizeRoleCode(request.getRoleCode());
        ScopeType scopeType = parseScopeType(request.getScopeType());
        Long scopeId = parseId(request.getScopeId(), "scope_id");
        validateCreateTimes(request.getValidFrom(), request.getValidTo());
        String key = requireIdempotencyKey(idempotencyKey);
        Long operatorId = currentUserProvider.requireUserId();

        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("user_id", String.valueOf(targetUserId));
        fingerprint.put("role_code", roleCode);
        fingerprint.put("scope_type", scopeType.name());
        fingerprint.put("scope_id", String.valueOf(scopeId));
        fingerprint.put("valid_from", request.getValidFrom());
        fingerprint.put("valid_to", request.getValidTo());

        return idempotencyService.execute(
                scopeOf(operatorId),
                OP_CREATE,
                key,
                fingerprint,
                IDEMPOTENCY_TTL,
                IdVersionVO.class,
                () -> doCreateAssignment(
                        targetUserId,
                        roleCode,
                        scopeType,
                        scopeId,
                        request.getValidFrom(),
                        request.getValidTo(),
                        operatorId
                )
        );
    }

    @Override
    public IdVersionVO updateAssignment(
            Long assignmentId,
            RoleAssignmentUpdateRequest request,
            String idempotencyKey
    ) {
        if (assignmentId == null) {
            throw invalidParam("assignment_id 不能为空");
        }
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        if (request.getVersion() == null || request.getVersion() < 0) {
            throw invalidParam("version 必须为非负整数");
        }
        if (!request.hasScope() && request.getActive() == null && !request.hasValidTo()) {
            throw invalidParam("scope_type、scope_id、active、valid_to 至少提交一项");
        }
        if (request.hasScope()
                && (!StringUtils.hasText(request.getScopeType())
                || !StringUtils.hasText(request.getScopeId()))) {
            throw invalidParam("修改范围时 scope_type 和 scope_id 必须同时提交");
        }
        String key = optionalIdempotencyKey(idempotencyKey);
        Long operatorId = currentUserProvider.requireUserId();

        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("assignment_id", String.valueOf(assignmentId));
        fingerprint.put("scope_present", request.hasScope());
        fingerprint.put("scope_type", request.hasScope() ? request.getScopeType() : null);
        fingerprint.put("scope_id", request.hasScope() ? request.getScopeId() : null);
        fingerprint.put("active", request.getActive());
        fingerprint.put("valid_to_present", request.hasValidTo());
        fingerprint.put("valid_to", request.hasValidTo() ? request.getValidTo() : null);
        fingerprint.put("version", request.getVersion());

        if (key == null) {
            return doUpdateAssignment(assignmentId, request, operatorId);
        }
        return idempotencyService.execute(
                scopeOf(operatorId),
                OP_UPDATE,
                key,
                fingerprint,
                IDEMPOTENCY_TTL,
                IdVersionVO.class,
                () -> doUpdateAssignment(assignmentId, request, operatorId)
        );
    }

    private IdVersionVO doCreateAssignment(
            Long targetUserId,
            String roleCode,
            ScopeType scopeType,
            Long scopeId,
            LocalDateTime validFrom,
            LocalDateTime validTo,
            Long operatorId
    ) {
        long startedAt = System.currentTimeMillis();
        AssignmentMutation mutation = transactionTemplate.execute(status -> {
            requireUser(targetUserId, true);
            SysRoleDO role = requireAssignableRole(roleCode);
            ScopeTarget target = resolveScopeTarget(roleCode, scopeType, scopeId);
            authorizeAssignment(operatorId, roleCode, target);

            long duplicates = roleAssignmentMapper.selectCount(
                    Wrappers.<SysUserRoleDO>lambdaQuery()
                            .eq(SysUserRoleDO::getUserId, targetUserId)
                            .eq(SysUserRoleDO::getRoleId, role.getId())
                            .eq(SysUserRoleDO::getScopeType, scopeType.name())
                            .eq(SysUserRoleDO::getScopeId, scopeId)
            );
            if (duplicates > 0) {
                throw duplicateAssignment();
            }

            SysUserRoleDO entity = new SysUserRoleDO();
            entity.setUserId(targetUserId);
            entity.setRoleId(role.getId());
            entity.setScopeType(scopeType.name());
            entity.setScopeId(scopeId);
            entity.setStatus(GRANT_ACTIVE);
            entity.setValidFrom(validFrom);
            entity.setValidTo(validTo);
            entity.setUpdatedAt(LocalDateTime.now());
            entity.setUpdatedBy(operatorId);
            entity.setDeleted(0);
            entity.setVersion(0);
            try {
                roleAssignmentMapper.insert(entity);
            } catch (DuplicateKeyException e) {
                throw duplicateAssignment();
            }

            IdVersionVO response = new IdVersionVO(
                    String.valueOf(entity.getId()),
                    entity.getVersion()
            );
            recordAudit(
                    operatorId,
                    OP_CREATE,
                    Map.of("after", assignmentSnapshot(entity, roleCode)),
                    startedAt
            );
            return new AssignmentMutation(response, targetUserId);
        });
        if (mutation == null) {
            throw internalError("范围授权创建事务未返回结果");
        }
        sideEffectService.revokeSessionsOfUsers(
                List.of(mutation.targetUserId()),
                OP_CREATE
        );
        return mutation.response();
    }

    private IdVersionVO doUpdateAssignment(
            Long assignmentId,
            RoleAssignmentUpdateRequest request,
            Long operatorId
    ) {
        long startedAt = System.currentTimeMillis();
        AssignmentMutation mutation = transactionTemplate.execute(status -> {
            SysUserRoleDO existing = requireScopedAssignment(assignmentId);
            if (!Objects.equals(existing.getVersion(), request.getVersion())) {
                throw versionConflict();
            }

            ScopeType oldScopeType = ScopeType.from(existing.getScopeType())
                    .orElseThrow(() -> invalidParam("现有授权范围类型非法"));
            Long oldScopeId = existing.getScopeId();
            ScopeType newScopeType = request.hasScope()
                    ? parseScopeType(request.getScopeType())
                    : oldScopeType;
            Long newScopeId = request.hasScope()
                    ? parseId(request.getScopeId(), "scope_id")
                    : oldScopeId;
            boolean newActive = request.getActive() == null
                    ? Objects.equals(GRANT_ACTIVE, existing.getStatus())
                    : request.getActive();
            LocalDateTime newValidTo = request.hasValidTo()
                    ? request.getValidTo()
                    : existing.getValidTo();
            requireUser(existing.getUserId(), newActive);

            SysRoleDO role = roleMapper.selectById(existing.getRoleId());
            if (role == null) {
                throw notFound("授权角色不存在");
            }
            String roleCode = normalizeRoleCode(role.getRoleCode());
            requireSupportedRoleCode(roleCode);

            ScopeTarget oldTarget = resolveScopeTarget(roleCode, oldScopeType, oldScopeId);
            authorizeAssignment(operatorId, roleCode, oldTarget);
            ScopeTarget newTarget = oldScopeType == newScopeType && oldScopeId.equals(newScopeId)
                    ? oldTarget
                    : resolveScopeTarget(roleCode, newScopeType, newScopeId);
            if (newTarget != oldTarget) {
                authorizeAssignment(operatorId, roleCode, newTarget);
            }
            validateUpdateTime(existing.getValidFrom(), newValidTo, newActive);

            if (!oldScopeType.equals(newScopeType) || !oldScopeId.equals(newScopeId)) {
                long duplicates = roleAssignmentMapper.selectCount(
                        Wrappers.<SysUserRoleDO>lambdaQuery()
                                .eq(SysUserRoleDO::getUserId, existing.getUserId())
                                .eq(SysUserRoleDO::getRoleId, existing.getRoleId())
                                .eq(SysUserRoleDO::getScopeType, newScopeType.name())
                                .eq(SysUserRoleDO::getScopeId, newScopeId)
                                .ne(SysUserRoleDO::getId, assignmentId)
                );
                if (duplicates > 0) {
                    throw duplicateAssignment();
                }
            }

            int newStatus = newActive ? GRANT_ACTIVE : GRANT_INACTIVE;
            try {
                int rows = roleAssignmentMapper.updateScopedAssignment(
                        assignmentId,
                        newScopeType.name(),
                        newScopeId,
                        newStatus,
                        newValidTo,
                        request.getVersion(),
                        operatorId
                );
                if (rows != 1) {
                    throw versionConflict();
                }
            } catch (DuplicateKeyException e) {
                throw duplicateAssignment();
            }

            int newVersion = request.getVersion() + 1;
            Map<String, Object> before = assignmentSnapshot(existing, roleCode);
            Map<String, Object> after = assignmentSnapshot(existing, roleCode);
            after.put("scope_type", newScopeType.name());
            after.put("scope_id", newScopeId);
            after.put("status", newStatus);
            after.put("valid_to", newValidTo);
            after.put("version", newVersion);
            recordAudit(
                    operatorId,
                    OP_UPDATE,
                    Map.of("before", before, "after", after),
                    startedAt
            );
            return new AssignmentMutation(
                    new IdVersionVO(String.valueOf(assignmentId), newVersion),
                    existing.getUserId()
            );
        });
        if (mutation == null) {
            throw internalError("范围授权修改事务未返回结果");
        }
        sideEffectService.revokeSessionsOfUsers(
                List.of(mutation.targetUserId()),
                OP_UPDATE
        );
        return mutation.response();
    }

    private void authorizeAssignment(
            Long operatorId,
            String roleCode,
            ScopeTarget target
    ) {
        List<EffectiveGrant> grants = organizationScopeService.loadEffectiveGrants(operatorId);
        boolean global = grants.stream()
                .anyMatch(grant -> ROLE_ADMIN.equals(grant.roleCode())
                        && grant.scopeType() == ScopeType.GLOBAL);
        if (global) {
            return;
        }
        if (!DELEGABLE_ROLES.contains(roleCode)) {
            throw dataForbidden("不能在当前范围内授予该管理员角色");
        }
        boolean allowed = grants.stream().anyMatch(grant ->
                managerGrantCoversTarget(grant, target)
        );
        if (!allowed) {
            throw dataForbidden("目标范围不在调用人的管理范围内");
        }
    }

    private boolean managerGrantCoversTarget(EffectiveGrant grant, ScopeTarget target) {
        String roleCode = normalizeNullableCode(grant.roleCode());
        if (roleCode == null || grant.scopeType() == null || grant.scopeId() == null) {
            return false;
        }
        if (target.type() == ScopeType.ORGANIZATION) {
            return grant.scopeType() == ScopeType.ORGANIZATION
                    && grant.scopeId().equals(target.scopeId())
                    && managesOrganizationType(roleCode, target.organization());
        }
        if (target.type() == ScopeType.PROVINCE) {
            return ROLE_PROVINCE_ADMIN.equals(roleCode)
                    && grant.scopeType() == ScopeType.PROVINCE
                    && grant.scopeId().equals(target.scopeId());
        }
        if (target.type() != ScopeType.EVENT) {
            return false;
        }
        ContestEditionScopeDO event = target.event();
        if (event == null) {
            return false;
        }
        if (grant.scopeType() == ScopeType.ORGANIZATION
                && event.getOwnerOrgId() != null
                && grant.scopeId().equals(event.getOwnerOrgId())) {
            return managesOrganizationType(roleCode, target.eventOwner());
        }
        if (grant.scopeType() == ScopeType.PROVINCE) {
            Long provinceCode = eventProvince(target);
            return ROLE_PROVINCE_ADMIN.equals(roleCode)
                    && provinceCode != null
                    && grant.scopeId().equals(provinceCode);
        }
        return false;
    }

    private boolean managesOrganizationType(String roleCode, OrganizationDO organization) {
        if (organization == null) {
            return false;
        }
        String orgType = normalizeNullableCode(organization.getOrgType());
        return (ROLE_SCHOOL_ADMIN.equals(roleCode) && ORG_SCHOOL.equals(orgType))
                || (ROLE_NATIONAL_ADMIN.equals(roleCode) && ORG_NATIONAL.equals(orgType));
    }

    private Long eventProvince(ScopeTarget target) {
        ContestEditionScopeDO event = target.event();
        if (event != null && StringUtils.hasText(event.getProvinceCode())) {
            return parseLongOrNull(event.getProvinceCode());
        }
        OrganizationDO owner = target.eventOwner();
        return owner == null ? null : parseLongOrNull(owner.getProvinceCode());
    }

    private ScopeTarget resolveScopeTarget(
            String roleCode,
            ScopeType scopeType,
            Long scopeId
    ) {
        if (scopeType == ScopeType.GLOBAL) {
            throw dataForbidden("EP-090/EP-091 不能授予全局范围");
        }
        if (scopeId == null || scopeId <= 0) {
            throw invalidParam("scope_id 必须为正整数");
        }

        if (scopeType == ScopeType.ORGANIZATION) {
            OrganizationDO organization = organizationMapper.selectById(scopeId);
            if (organization == null
                    || !Objects.equals(ORGANIZATION_ACTIVE, organization.getStatus())) {
                throw notFound("目标组织不存在或不可用");
            }
            requireRoleScopeCompatibility(roleCode, scopeType, organization, null);
            return new ScopeTarget(scopeType, scopeId, organization, null, null);
        }

        if (scopeType == ScopeType.PROVINCE) {
            String provinceCode = String.valueOf(scopeId);
            if (!dictionaryQueryService.containsEnabledValue(
                    PROVINCE_DICTIONARY,
                    provinceCode
            )) {
                throw notFound("目标省份不存在或不可用");
            }
            requireRoleScopeCompatibility(roleCode, scopeType, null, null);
            return new ScopeTarget(scopeType, scopeId, null, null, null);
        }

        ContestEditionScopeDO event = contestEditionMapper.selectById(scopeId);
        if (event == null) {
            throw notFound("目标赛事不存在");
        }
        OrganizationDO owner = event.getOwnerOrgId() == null
                ? null
                : organizationMapper.selectById(event.getOwnerOrgId());
        requireRoleScopeCompatibility(roleCode, scopeType, null, event);
        return new ScopeTarget(scopeType, scopeId, null, event, owner);
    }

    private void requireRoleScopeCompatibility(
            String roleCode,
            ScopeType scopeType,
            OrganizationDO organization,
            ContestEditionScopeDO event
    ) {
        switch (roleCode) {
            case ROLE_SCHOOL_ADMIN -> {
                if (scopeType != ScopeType.ORGANIZATION
                        || organization == null
                        || !ORG_SCHOOL.equals(normalizeNullableCode(organization.getOrgType()))) {
                    throw invalidParam("SCHOOL_ADMIN 只能绑定 SCHOOL 组织");
                }
            }
            case ROLE_PROVINCE_ADMIN -> {
                if (scopeType != ScopeType.PROVINCE) {
                    throw invalidParam("PROVINCE_ADMIN 只能绑定省份范围");
                }
            }
            case ROLE_NATIONAL_ADMIN -> {
                if (scopeType != ScopeType.ORGANIZATION
                        || organization == null
                        || !ORG_NATIONAL.equals(normalizeNullableCode(organization.getOrgType()))) {
                    throw invalidParam("NATIONAL_ADMIN 只能绑定国家级主办方组织");
                }
            }
            case ROLE_EVENT_MANAGER, ROLE_REVIEWER -> {
                if (scopeType == ScopeType.EVENT && event == null) {
                    throw invalidParam("赛事范围必须绑定有效赛事");
                }
            }
            default -> throw dataForbidden("当前角色不能通过范围授权接口授予");
        }
    }

    private SysRoleDO requireAssignableRole(String roleCode) {
        requireSupportedRoleCode(roleCode);
        SysRoleDO role = roleMapper.selectOne(
                Wrappers.<SysRoleDO>lambdaQuery().eq(SysRoleDO::getRoleCode, roleCode)
        );
        if (role == null) {
            throw invalidParam("role_code 不存在");
        }
        return role;
    }

    private void requireSupportedRoleCode(String roleCode) {
        if (ROLE_ADMIN.equals(roleCode) || !SUPPORTED_ROLES.contains(roleCode)) {
            throw dataForbidden("不能授予全局 ADMIN 或未开放的范围角色");
        }
    }

    private SysUserRoleDO requireScopedAssignment(Long assignmentId) {
        SysUserRoleDO assignment = roleAssignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw notFound("范围授权不存在");
        }
        ScopeType existingType = ScopeType.from(assignment.getScopeType())
                .orElseThrow(() -> invalidParam("现有授权范围类型非法"));
        if (existingType == ScopeType.GLOBAL
                || (assignment.getScopeId() != null && assignment.getScopeId() == 0L)) {
            throw invalidParam("GLOBAL/0 基础角色由 EP-007 维护");
        }
        return assignment;
    }

    private SysUserDO requireUser(Long userId, boolean requireActive) {
        SysUserDO user = userMapper.selectById(userId);
        if (user == null
                || (requireActive && !Objects.equals(USER_ACTIVE, user.getStatus()))) {
            throw notFound("目标用户不存在或不可用");
        }
        return user;
    }

    private void validateCreateTimes(LocalDateTime validFrom, LocalDateTime validTo) {
        LocalDateTime now = LocalDateTime.now();
        if (validTo != null && !validTo.isAfter(now)) {
            throw invalidParam("valid_to 必须晚于当前时间");
        }
        if (validFrom != null && validTo != null && !validTo.isAfter(validFrom)) {
            throw invalidParam("valid_to 必须晚于 valid_from");
        }
    }

    private void validateUpdateTime(
            LocalDateTime validFrom,
            LocalDateTime validTo,
            boolean active
    ) {
        if (validTo == null) {
            return;
        }
        if (active && !validTo.isAfter(LocalDateTime.now())) {
            throw invalidParam("启用授权的 valid_to 必须晚于当前时间");
        }
        if (validFrom != null && !validTo.isAfter(validFrom)) {
            throw invalidParam("valid_to 必须晚于 valid_from");
        }
    }

    private Map<String, Object> assignmentSnapshot(
            SysUserRoleDO assignment,
            String roleCode
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("assignment_id", assignment.getId() == null
                ? null
                : String.valueOf(assignment.getId()));
        snapshot.put("user_id", assignment.getUserId() == null
                ? null
                : String.valueOf(assignment.getUserId()));
        snapshot.put("role_code", roleCode);
        snapshot.put("scope_type", assignment.getScopeType());
        snapshot.put("scope_id", assignment.getScopeId() == null
                ? null
                : String.valueOf(assignment.getScopeId()));
        snapshot.put("status", assignment.getStatus());
        snapshot.put("valid_from", assignment.getValidFrom());
        snapshot.put("valid_to", assignment.getValidTo());
        snapshot.put("version", assignment.getVersion());
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

    private ScopeType parseScopeType(String value) {
        return ScopeType.from(value)
                .orElseThrow(() -> invalidParam("scope_type 非法"));
    }

    private Long parseId(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw invalidParam(field + " 不能为空");
        }
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed <= 0) {
                throw invalidParam(field + " 必须为正整数");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw invalidParam(field + " 必须为数字 ID");
        }
    }

    private Long parseLongOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeRoleCode(String value) {
        String roleCode = normalizeNullableCode(value);
        if (roleCode == null) {
            throw invalidParam("role_code 不能为空");
        }
        return roleCode;
    }

    private String normalizeNullableCode(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : null;
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

    private BizException duplicateAssignment() {
        return new BizException(
                ErrorCode.DUPLICATE_RELATION.getCode(),
                "目标用户已存在相同角色和范围授权"
        );
    }

    private BizException versionConflict() {
        return new BizException(
                ErrorCode.BUSINESS_CONFLICT.getCode(),
                "范围授权已被他人修改，请刷新后重试"
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

    private record AssignmentMutation(
            IdVersionVO response,
            Long targetUserId
    ) {
    }

    private record ScopeTarget(
            ScopeType type,
            Long scopeId,
            OrganizationDO organization,
            ContestEditionScopeDO event,
            OrganizationDO eventOwner
    ) {
    }
}
