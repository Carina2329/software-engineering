package com.campus.contest.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyScope;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.system.dto.AuthorizationSnapshot;
import com.campus.contest.module.system.dto.UserAccessUpdateRequest;
import com.campus.contest.module.system.dto.UserPageQuery;
import com.campus.contest.module.system.entity.SysRoleDO;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.entity.SysUserRoleDO;
import com.campus.contest.module.system.mapper.SysRoleMapper;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.system.mapper.SysUserRoleMapper;
import com.campus.contest.module.system.service.AuthorizationSnapshotService;
import com.campus.contest.module.system.service.RbacSideEffectService;
import com.campus.contest.module.system.service.UserAccessService;
import com.campus.contest.module.system.vo.CurrentUserVO;
import com.campus.contest.module.system.vo.IdVersionVO;
import com.campus.contest.module.system.vo.UserItemVO;
import com.campus.contest.module.system.vo.UserPageVO;
import com.campus.contest.security.CurrentUserProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户列表与用户角色分配实现。
 *
 * user_type 不是库内字段，而是由用户当前角色推导：ADMIN > TEACHER > REVIEWER > STUDENT > 其他。
 * 修改用户角色或状态后，必须撤销该用户全部旧会话，使旧 JWT 立即失效。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAccessServiceImpl implements UserAccessService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String OP_UPDATE = "RBAC_USER_ACCESS_UPDATE";

    private static final List<String> TYPE_PRIORITY = List.of("ADMIN", "TEACHER", "REVIEWER", "STUDENT");

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final IdempotencyService idempotencyService;
    private final RbacSideEffectService sideEffectService;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final AuthorizationSnapshotService authorizationSnapshotService;

    // ------------------------------------------------------------------
    // EP-003 当前用户
    // ------------------------------------------------------------------

    /**
     * EP-003：返回登录用户档案、角色、数据范围与权限摘要。
     *
     * 档案字段取自 sys_user；角色、权限、数据范围统一取自 AuthorizationSnapshotService，
     * 与登录时写进 Redis 会话的快照同源，避免前端展示的权限和后端实际授权不一致。
     */
    @Override
    public CurrentUserVO currentUserProfile() {
        Long userId = currentUserProvider.requireUserId();
        // 角色、权限、数据范围统一走登录时使用的同一份快照实现，
        // 避免这里再写一套 sys_user_role / sys_role_menu 的组装逻辑而和会话不一致。
        AuthorizationSnapshot snapshot = authorizationSnapshotService.loadRequired(userId);
        SysUserDO user = userMapper.selectById(snapshot.userId());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED.getCode(), "账号或凭证不正确");
        }

        CurrentUserVO vo = new CurrentUserVO();
        vo.setId(String.valueOf(user.getId()));
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setUserType(deriveUserType(new ArrayList<>(snapshot.roleCodes())));
        vo.setCollege(user.getCollege());
        vo.setMajor(user.getMajor());
        vo.setGrade(user.getGrade());
        vo.setSkillTags(parseSkillTags(user.getSkillTags()));
        vo.setRoles(new ArrayList<>(snapshot.roleCodes()));
        vo.setDataScopes(new ArrayList<>(snapshot.dataScopes()));
        vo.setPermissions(new ArrayList<>(snapshot.permissionCodes()));
        return vo;
    }

    @Override
    public UserPageVO pageUsers(UserPageQuery query) {
        UserPageQuery condition = query == null ? new UserPageQuery() : query;
        int page = condition.getPage() == null ? DEFAULT_PAGE : condition.getPage();
        int size = condition.getSize() == null ? DEFAULT_SIZE : condition.getSize();
        if (page < 1) {
            throw invalidParam("page 必须大于等于 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw invalidParam("size 必须为 1-" + MAX_SIZE);
        }

        LambdaQueryWrapper<SysUserDO> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(condition.getUsername())) {
            wrapper.like(SysUserDO::getUsername, condition.getUsername().trim());
        }
        if (condition.getStatus() != null) {
            if (condition.getStatus() != 0 && condition.getStatus() != 1) {
                throw invalidParam("status 只能是 0 或 1");
            }
            wrapper.eq(SysUserDO::getStatus, condition.getStatus());
        }
        if (StringUtils.hasText(condition.getUserType())) {
            List<Long> userIds = userIdsOfType(condition.getUserType().trim().toUpperCase(Locale.ROOT));
            if (userIds.isEmpty()) {
                return UserPageVO.empty(page, size);
            }
            wrapper.in(SysUserDO::getId, userIds);
        }
        applySort(wrapper, condition.getSort());

        IPage<SysUserDO> paged = userMapper.selectPage(new Page<>(page, size), wrapper);
        List<UserItemVO> items = toItems(paged.getRecords());
        long total = paged.getTotal();
        boolean hasMore = (long) page * size < total;
        return new UserPageVO(items, total, page, size, hasMore);
    }

    @Override
    public IdVersionVO updateUserAccess(
            Long userId,
            UserAccessUpdateRequest request,
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
        if (request.getStatus() == null && request.getRoleCodes() == null) {
            throw invalidParam("status 与 role_codes 至少提交一项");
        }
        Integer status = request.getStatus();
        if (status != null && status != 0 && status != 1) {
            throw invalidParam("status 只能是 0 或 1");
        }
        List<String> roleCodes = request.getRoleCodes() == null
                ? null
                : normalizeRoleCodes(request.getRoleCodes());

        Long operatorId = currentUserProvider.requireUserId();
        // PATCH /users/{user_id} 在 OpenAPI 中 x-idempotency = 是，且 Idempotency-Key 为必填，
        // 因此这里不再允许“缺 key 直接写库”，否则重试会重复改角色并重复撤会话。
        if (!StringUtils.hasText(idempotencyKey)) {
            throw invalidParam("缺少 Idempotency-Key 请求头");
        }
        String key = idempotencyKey.trim();
        if (key.length() > 64) {
            throw invalidParam("Idempotency-Key 必须为 1-64 位非空字符串");
        }

        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("user_id", String.valueOf(userId));
        fingerprint.put("status", status);
        fingerprint.put("role_codes", roleCodes);
        fingerprint.put("version", request.getVersion());

        return idempotencyService.execute(
                new IdempotencyScope(IdempotencyScope.PrincipalType.USER, String.valueOf(operatorId)),
                OP_UPDATE,
                key,
                fingerprint,
                IDEMPOTENCY_TTL,
                IdVersionVO.class,
                () -> doUpdateUserAccess(userId, status, roleCodes, request.getVersion(), operatorId)
        );
    }

    // ------------------------------------------------------------------
    // 写操作实现
    // ------------------------------------------------------------------

    private IdVersionVO doUpdateUserAccess(
            Long userId,
            Integer status,
            List<String> roleCodes,
            Integer version,
            Long operatorId
    ) {
        SysUserDO existing = requireUser(userId);
        List<String> beforeRoles = roleCodesOfUser(userId);
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("user_id", String.valueOf(userId));
        before.put("status", existing.getStatus());
        before.put("role_codes", beforeRoles);
        before.put("version", existing.getVersion());

        List<Long> roleIds = roleCodes == null ? null : resolveRoleIds(roleCodes);

        Long newVersion = transactionTemplate.execute(txStatus -> {
            SysUserDO update = new SysUserDO();
            update.setId(userId);
            update.setStatus(status);
            update.setUpdatedAt(LocalDateTime.now());
            update.setUpdatedBy(operatorId);
            update.setVersion(version);
            int rows = userMapper.updateById(update);
            if (rows != 1) {
                throw versionConflict();
            }
            if (roleIds != null) {
                userRoleMapper.delete(
                        Wrappers.<SysUserRoleDO>lambdaQuery().eq(SysUserRoleDO::getUserId, userId)
                );
                for (Long roleId : roleIds) {
                    SysUserRoleDO relation = new SysUserRoleDO();
                    relation.setUserId(userId);
                    relation.setRoleId(roleId);
                    userRoleMapper.insert(relation);
                }
            }
            return (long) (version + 1);
        });
        if (newVersion == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "用户更新事务未返回结果");
        }

        Integer effectiveStatus = status == null ? existing.getStatus() : status;
        List<String> afterRoles = roleCodes == null ? beforeRoles : roleCodes;
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("user_id", String.valueOf(userId));
        after.put("status", effectiveStatus);
        after.put("role_codes", afterRoles);
        after.put("version", newVersion);

        sideEffectService.audit(
                operatorId,
                "USER_ACCESS_UPDATE",
                params(Map.of("before", before, "after", after)),
                0
        );

        boolean rolesChanged = roleCodes != null && !new LinkedHashSet<>(beforeRoles).equals(new LinkedHashSet<>(afterRoles));
        boolean statusChanged = status != null && !Objects.equals(existing.getStatus(), status);
        if (rolesChanged || statusChanged) {
            // 角色或状态变化后，旧会话必须立即失效
            sideEffectService.revokeSessionsOfUsers(List.of(userId), "USER_ACCESS_UPDATE");
        }
        return new IdVersionVO(String.valueOf(userId), Math.toIntExact(newVersion));
    }

    // ------------------------------------------------------------------
    // 查询辅助
    // ------------------------------------------------------------------

    private SysUserDO requireUser(Long userId) {
        SysUserDO user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND.getCode(), "用户不存在或对当前用户不可见");
        }
        return user;
    }

    private List<String> normalizeRoleCodes(List<String> roleCodes) {
        if (roleCodes.isEmpty()) {
            throw invalidParam("role_codes 不能为空，至少保留一个角色");
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String raw : roleCodes) {
            if (raw == null || raw.isBlank()) {
                throw invalidParam("role_codes 不能包含空值");
            }
            codes.add(raw.trim().toUpperCase(Locale.ROOT));
        }
        return new ArrayList<>(codes);
    }

    private List<Long> resolveRoleIds(List<String> roleCodes) {
        List<SysRoleDO> roles = roleMapper.selectList(
                Wrappers.<SysRoleDO>lambdaQuery().in(SysRoleDO::getRoleCode, roleCodes)
        );
        if (roles.size() != roleCodes.size()) {
            throw invalidParam("role_codes 包含不存在的角色");
        }
        return roles.stream()
                .sorted((left, right) -> Long.compare(left.getId(), right.getId()))
                .map(SysRoleDO::getId)
                .toList();
    }

    private List<Long> userIdsOfType(String userType) {
        SysRoleDO role = roleMapper.selectOne(
                Wrappers.<SysRoleDO>lambdaQuery().eq(SysRoleDO::getRoleCode, userType)
        );
        if (role == null) {
            return List.of();
        }
        return userRoleMapper.selectList(
                        Wrappers.<SysUserRoleDO>lambdaQuery().eq(SysUserRoleDO::getRoleId, role.getId())
                ).stream()
                .map(SysUserRoleDO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /** skill_tags 在库内是逗号分隔字符串，这里转成数组；空值返回空数组而不是 null */
    private List<String> parseSkillTags(String skillTags) {
        if (!StringUtils.hasText(skillTags)) {
            return new ArrayList<>();
        }
        List<String> tags = new ArrayList<>();
        for (String raw : skillTags.split("[,，;；]")) {
            String tag = raw.trim();
            if (!tag.isEmpty()) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private List<String> roleCodesOfUser(Long userId) {
        List<SysUserRoleDO> links = userRoleMapper.selectList(
                Wrappers.<SysUserRoleDO>lambdaQuery().eq(SysUserRoleDO::getUserId, userId)
        );
        return roleCodesOf(links).getOrDefault(userId, new ArrayList<>());
    }

    /** 批量把 user_id -> role_code 列表，按 role_id 升序保持稳定顺序 */
    private Map<Long, List<String>> roleCodesOf(Collection<SysUserRoleDO> links) {
        if (links == null || links.isEmpty()) {
            return Map.of();
        }
        Set<Long> roleIds = links.stream()
                .map(SysUserRoleDO::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, SysRoleDO> roleById = Optional.ofNullable(roleMapper.selectBatchIds(roleIds))
                .orElseGet(List::of)
                .stream()
                .collect(Collectors.toMap(SysRoleDO::getId, role -> role, (left, right) -> left));
        Map<Long, List<Long>> roleIdsByUser = new LinkedHashMap<>();
        for (SysUserRoleDO link : links) {
            if (link.getUserId() == null || link.getRoleId() == null) {
                continue;
            }
            roleIdsByUser.computeIfAbsent(link.getUserId(), key -> new ArrayList<>()).add(link.getRoleId());
        }
        Map<Long, List<String>> result = new LinkedHashMap<>();
        roleIdsByUser.forEach((userId, ids) -> {
            List<String> codes = ids.stream()
                    .sorted()
                    .map(roleById::get)
                    .filter(Objects::nonNull)
                    .map(SysRoleDO::getRoleCode)
                    .toList();
            result.put(userId, codes);
        });
        return result;
    }

    private List<UserItemVO> toItems(List<SysUserDO> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = users.stream().map(SysUserDO::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, List<String>> codesByUser = roleCodesOf(
                userRoleMapper.selectList(
                        Wrappers.<SysUserRoleDO>lambdaQuery().in(SysUserRoleDO::getUserId, userIds)
                )
        );
        List<UserItemVO> items = new ArrayList<>(users.size());
        for (SysUserDO user : users) {
            UserItemVO item = new UserItemVO();
            item.setId(String.valueOf(user.getId()));
            item.setUsername(user.getUsername());
            item.setRealName(user.getRealName());
            item.setStatus(user.getStatus());
            item.setVersion(user.getVersion());
            List<String> codes = codesByUser.getOrDefault(user.getId(), List.of());
            item.setRoleCodes(new ArrayList<>(codes));
            item.setUserType(deriveUserType(codes));
            items.add(item);
        }
        return items;
    }

    private String deriveUserType(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return "";
        }
        for (String candidate : TYPE_PRIORITY) {
            if (codes.contains(candidate)) {
                return candidate;
            }
        }
        return codes.get(0);
    }

    private void applySort(LambdaQueryWrapper<SysUserDO> wrapper, String sort) {
        String raw = StringUtils.hasText(sort) ? sort.trim() : "id";
        boolean asc = !raw.startsWith("-");
        String field = asc ? raw : raw.substring(1);
        switch (field) {
            case "id" -> wrapper.orderBy(true, asc, SysUserDO::getId);
            case "username" -> wrapper.orderBy(true, asc, SysUserDO::getUsername);
            case "status" -> wrapper.orderBy(true, asc, SysUserDO::getStatus);
            case "created_at" -> wrapper.orderBy(true, asc, SysUserDO::getCreatedAt);
            case "updated_at" -> wrapper.orderBy(true, asc, SysUserDO::getUpdatedAt);
            default -> throw invalidParam("sort 只支持 id、username、status、created_at、updated_at");
        }
    }

    private String params(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("审计参数序列化失败: {}", e.getClass().getSimpleName());
            return "{}";
        }
    }

    private BizException versionConflict() {
        return new BizException(ErrorCode.BUSINESS_CONFLICT.getCode(), "数据已被他人修改，请刷新后重试");
    }

    private BizException invalidParam(String message) {
        return new BizException(ErrorCode.INVALID_PARAM.getCode(), message);
    }
}
