package com.campus.contest.module.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyScope;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.system.dto.RoleCreateRequest;
import com.campus.contest.module.system.dto.RolePermissionUpdateRequest;
import com.campus.contest.module.system.dto.RoleUpdateRequest;
import com.campus.contest.module.system.entity.SysMenuDO;
import com.campus.contest.module.system.entity.SysRoleDO;
import com.campus.contest.module.system.entity.SysRoleMenuDO;
import com.campus.contest.module.system.entity.SysUserRoleDO;
import com.campus.contest.module.system.mapper.SysMenuMapper;
import com.campus.contest.module.system.mapper.SysRoleMapper;
import com.campus.contest.module.system.mapper.SysRoleMenuMapper;
import com.campus.contest.module.system.mapper.SysUserRoleMapper;
import com.campus.contest.module.system.service.PermissionResourceService;
import com.campus.contest.module.system.service.RbacSideEffectService;
import com.campus.contest.module.system.service.RoleService;
import com.campus.contest.module.system.vo.IdVersionVO;
import com.campus.contest.module.system.vo.RolePermissionVO;
import com.campus.contest.module.system.vo.RoleVO;
import com.campus.contest.module.system.vo.RoleVersionVO;
import com.campus.contest.security.CurrentUserProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 角色管理与角色授权实现。
 *
 * 关键约束：
 * 1. 角色创建、修改、授权都使用带用户作用域的 Idempotency-Key；
 * 2. 修改和授权使用 sys_role.version 乐观锁；
 * 3. MySQL 事务提交后才撤销受影响用户的 Redis 会话，撤销失败返回 503 + 50003；
 * 4. 审计日志只记录角色编码、名称、数据范围和权限资源 ID，不记录敏感数据。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoleServiceImpl implements RoleService {

    private static final Set<String> DATA_SCOPES = Set.of("ALL", "SELF", "TEAM", "GUIDED", "ASSIGNED");
    private static final String ROLE_CODE_REGEX = "^[A-Z][A-Z0-9_]{1,15}$";
    private static final int ROLE_NAME_MAX_LENGTH = 32;
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private static final String OP_CREATE = "RBAC_ROLE_CREATE";
    private static final String OP_UPDATE = "RBAC_ROLE_UPDATE";
    private static final String OP_AUTHORIZE = "RBAC_ROLE_AUTHORIZE";

    private static final String PERMISSION_ENABLED = "1";

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMenuMapper menuMapper;
    private final PermissionResourceService permissionResourceService;
    private final IdempotencyService idempotencyService;
    private final RbacSideEffectService sideEffectService;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public List<RoleVO> listRoles() {
        List<SysRoleDO> roles = roleMapper.selectList(
                Wrappers.<SysRoleDO>lambdaQuery()
                        .orderByAsc(SysRoleDO::getId)
        );
        return roles.stream().map(RoleVO::from).toList();
    }

    @Override
    public RoleVO createRole(RoleCreateRequest request, String idempotencyKey) {
        String roleCode = normalizeCode(request == null ? null : request.getRoleCode());
        String roleName = normalizeRoleName(request == null ? null : request.getRoleName());
        String dataScope = normalizeDataScope(request == null ? null : request.getDataScope());
        requireIdempotencyKey(idempotencyKey);
        Long operatorId = currentUserProvider.requireUserId();

        Map<String, Object> fingerprint = Map.of(
                "role_code", roleCode,
                "role_name", roleName,
                "data_scope", dataScope
        );
        return idempotencyService.execute(
                scopeOf(operatorId),
                OP_CREATE,
                idempotencyKey,
                fingerprint,
                IDEMPOTENCY_TTL,
                RoleVO.class,
                () -> doCreateRole(roleCode, roleName, dataScope, operatorId)
        );
    }

    @Override
    public IdVersionVO updateRole(Long roleId, RoleUpdateRequest request, String idempotencyKey) {
        if (roleId == null) {
            throw invalidParam("role_id 不能为空");
        }
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        requireVersion(request.getVersion());
        String roleName = request.getRoleName() == null ? null : normalizeRoleName(request.getRoleName());
        String dataScope = request.getDataScope() == null
                ? null
                : normalizeDataScope(request.getDataScope());
        if (roleName == null && dataScope == null) {
            throw invalidParam("role_name 与 data_scope 至少提交一项");
        }
        requireIdempotencyKey(idempotencyKey);
        Long operatorId = currentUserProvider.requireUserId();

        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("role_id", String.valueOf(roleId));
        fingerprint.put("role_name", roleName);
        fingerprint.put("data_scope", dataScope);
        fingerprint.put("version", request.getVersion());

        return idempotencyService.execute(
                scopeOf(operatorId),
                OP_UPDATE,
                idempotencyKey,
                fingerprint,
                IDEMPOTENCY_TTL,
                IdVersionVO.class,
                () -> doUpdateRole(roleId, roleName, dataScope, request.getVersion(), operatorId)
        );
    }

    @Override
    public RolePermissionVO getRolePermissions(Long roleId) {
        SysRoleDO role = requireRole(roleId);
        return RolePermissionVO.of(
                role.getId(),
                role.getVersion(),
                grantedMenuIds(role.getId()),
                permissionResourceService.menuTree()
        );
    }

    @Override
    public RoleVersionVO replaceRolePermissions(
            Long roleId,
            RolePermissionUpdateRequest request,
            String idempotencyKey
    ) {
        if (roleId == null) {
            throw invalidParam("role_id 不能为空");
        }
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        requireVersion(request.getVersion());
        List<Long> menuIds = normalizeMenuIds(request.getMenuIds());
        requireIdempotencyKey(idempotencyKey);
        Long operatorId = currentUserProvider.requireUserId();

        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("role_id", String.valueOf(roleId));
        fingerprint.put("menu_ids", menuIds.stream().sorted().map(String::valueOf).toList());
        fingerprint.put("version", request.getVersion());

        return idempotencyService.execute(
                scopeOf(operatorId),
                OP_AUTHORIZE,
                idempotencyKey,
                fingerprint,
                IDEMPOTENCY_TTL,
                RoleVersionVO.class,
                () -> doReplaceRolePermissions(roleId, menuIds, request.getVersion(), operatorId)
        );
    }

    // ------------------------------------------------------------------
    // 写操作实现
    // ------------------------------------------------------------------

    private RoleVO doCreateRole(String roleCode, String roleName, String dataScope, Long operatorId) {
        if (existsRoleCode(roleCode)) {
            throw duplicateRoleCode();
        }

        SysRoleDO entity = new SysRoleDO();
        entity.setRoleCode(roleCode);
        entity.setRoleName(roleName);
        entity.setDataScope(dataScope);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        entity.setDeleted(0);
        entity.setVersion(0);
        try {
            roleMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            throw duplicateRoleCode();
        }

        sideEffectService.audit(
                operatorId,
                "ROLE_CREATE",
                params(Map.of(
                        "role_id", String.valueOf(entity.getId()),
                        "role_code", roleCode,
                        "role_name", roleName,
                        "data_scope", dataScope
                )),
                0
        );
        return RoleVO.from(entity);
    }

    private IdVersionVO doUpdateRole(
            Long roleId,
            String roleName,
            String dataScope,
            Integer version,
            Long operatorId
    ) {
        SysRoleDO existing = requireRole(roleId);
        String effectiveName = roleName == null ? existing.getRoleName() : roleName;
        String effectiveScope = dataScope == null ? existing.getDataScope() : dataScope;
        Map<String, Object> before = roleSnapshot(existing);

        SysRoleDO update = new SysRoleDO();
        update.setId(roleId);
        update.setRoleName(effectiveName);
        update.setDataScope(effectiveScope);
        update.setUpdatedAt(LocalDateTime.now());
        update.setUpdatedBy(operatorId);
        update.setVersion(version);
        int rows = roleMapper.updateById(update);
        if (rows != 1) {
            throw versionConflict();
        }
        Integer newVersion = version + 1;

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("role_id", String.valueOf(roleId));
        after.put("role_name", effectiveName);
        after.put("data_scope", effectiveScope);
        after.put("version", newVersion);

        sideEffectService.audit(
                operatorId,
                "ROLE_UPDATE",
                params(Map.of("before", before, "after", after)),
                0
        );

        boolean scopeChanged = !Objects.equals(existing.getDataScope(), effectiveScope);
        if (scopeChanged) {
            // 数据范围变化会改变用户可见数据，必须撤销该角色下所有用户的旧会话
            sideEffectService.revokeSessionsOfUsers(userIdsOfRole(roleId), "ROLE_UPDATE");
        }
        return new IdVersionVO(String.valueOf(roleId), newVersion);
    }

    private RoleVersionVO doReplaceRolePermissions(
            Long roleId,
            List<Long> menuIds,
            Integer version,
            Long operatorId
    ) {
        SysRoleDO role = requireRole(roleId);
        ensureMenusUsable(menuIds);
        List<String> before = grantedMenuIds(roleId);

        Long newVersion = transactionTemplate.execute(status -> {
            SysRoleDO update = new SysRoleDO();
            update.setId(roleId);
            update.setUpdatedAt(LocalDateTime.now());
            update.setUpdatedBy(operatorId);
            update.setVersion(version);
            int rows = roleMapper.updateById(update);
            if (rows != 1) {
                throw versionConflict();
            }
            roleMenuMapper.delete(
                    Wrappers.<SysRoleMenuDO>lambdaQuery().eq(SysRoleMenuDO::getRoleId, roleId)
            );
            for (Long menuId : menuIds) {
                SysRoleMenuDO relation = new SysRoleMenuDO();
                relation.setRoleId(roleId);
                relation.setMenuId(menuId);
                roleMenuMapper.insert(relation);
            }
            return (long) (version + 1);
        });

        if (newVersion == null) {
            throw new BizException(
                    ErrorCode.INTERNAL_ERROR.getCode(),
                    "角色授权事务未返回结果"
            );
        }

        sideEffectService.audit(
                operatorId,
                "ROLE_AUTHORIZE",
                params(Map.of(
                        "role_id", String.valueOf(roleId),
                        "before_menu_ids", before,
                        "after_menu_ids", menuIds.stream().map(String::valueOf).toList(),
                        "version", newVersion
                )),
                0
        );

        // 角色权限变化后，拥有该角色的用户旧会话立即失效
        sideEffectService.revokeSessionsOfUsers(userIdsOfRole(roleId), "ROLE_AUTHORIZE");
        return new RoleVersionVO(String.valueOf(roleId), Math.toIntExact(newVersion));
    }

    // ------------------------------------------------------------------
    // 查询与校验
    // ------------------------------------------------------------------

    private SysRoleDO requireRole(Long roleId) {
        SysRoleDO role = roleId == null ? null : roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(
                    ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                    "角色不存在或对当前用户不可见"
            );
        }
        return role;
    }

    private List<String> grantedMenuIds(Long roleId) {
        return roleMenuMapper.selectList(
                        Wrappers.<SysRoleMenuDO>lambdaQuery().eq(SysRoleMenuDO::getRoleId, roleId)
                ).stream()
                .map(SysRoleMenuDO::getMenuId)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }

    private List<Long> userIdsOfRole(Long roleId) {
        return userRoleMapper.selectList(
                        Wrappers.<SysUserRoleDO>lambdaQuery().eq(SysUserRoleDO::getRoleId, roleId)
                ).stream()
                .map(SysUserRoleDO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private boolean existsRoleCode(String roleCode) {
        return roleMapper.selectCount(
                Wrappers.<SysRoleDO>lambdaQuery().eq(SysRoleDO::getRoleCode, roleCode)
        ) > 0;
    }

    private void ensureMenusUsable(List<Long> menuIds) {
        if (menuIds.isEmpty()) {
            return;
        }
        List<SysMenuDO> menus = menuMapper.selectBatchIds(menuIds);
        if (menus.size() != menuIds.size()) {
            throw invalidParam("menu_ids 包含不存在的权限资源");
        }
        boolean disabled = menus.stream()
                .anyMatch(menu -> !PERMISSION_ENABLED.equals(String.valueOf(menu.getStatus())));
        if (disabled) {
            throw invalidParam("menu_ids 包含已停用的权限资源");
        }
    }

    private List<Long> normalizeMenuIds(List<String> menuIds) {
        if (menuIds == null) {
            throw invalidParam("menu_ids 不能为空");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String raw : menuIds) {
            if (raw == null || raw.isBlank()) {
                throw invalidParam("menu_ids 不能包含空值");
            }
            try {
                ids.add(Long.valueOf(raw.trim()));
            } catch (NumberFormatException e) {
                throw invalidParam("menu_ids 必须是数字 ID");
            }
        }
        return new ArrayList<>(ids);
    }

    private String normalizeCode(String roleCode) {
        String value = roleCode == null ? "" : roleCode.trim();
        if (!value.matches(ROLE_CODE_REGEX)) {
            throw invalidParam("role_code 必须为 2-16 位大写字母、数字或下划线，且以字母开头");
        }
        return value;
    }

    private String normalizeRoleName(String roleName) {
        String value = roleName == null ? "" : roleName.trim();
        if (value.isEmpty() || value.length() > ROLE_NAME_MAX_LENGTH) {
            throw invalidParam("role_name 长度必须为 1-32");
        }
        return value;
    }

    private String normalizeDataScope(String dataScope) {
        String value = dataScope == null ? "" : dataScope.trim().toUpperCase(Locale.ROOT);
        if (!DATA_SCOPES.contains(value)) {
            throw invalidParam("data_scope 只能是 ALL、SELF、TEAM、GUIDED、ASSIGNED");
        }
        return value;
    }

    private void requireVersion(Integer version) {
        if (version == null || version < 0) {
            throw invalidParam("version 必须为非负整数");
        }
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw invalidParam("Idempotency-Key 必须为 1-64 位非空字符串");
        }
    }

    private IdempotencyScope scopeOf(Long operatorId) {
        return new IdempotencyScope(IdempotencyScope.PrincipalType.USER, String.valueOf(operatorId));
    }

    private Map<String, Object> roleSnapshot(SysRoleDO role) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("role_id", role.getId() == null ? null : String.valueOf(role.getId()));
        snapshot.put("role_code", role.getRoleCode());
        snapshot.put("role_name", role.getRoleName());
        snapshot.put("data_scope", role.getDataScope());
        snapshot.put("version", role.getVersion());
        return snapshot;
    }

    private String params(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("审计参数序列化失败: {}", e.getClass().getSimpleName());
            return "{}";
        }
    }

    private BizException duplicateRoleCode() {
        return new BizException(ErrorCode.DUPLICATE_RELATION.getCode(), "角色编码已存在");
    }

    private BizException versionConflict() {
        return new BizException(
                ErrorCode.BUSINESS_CONFLICT.getCode(),
                "数据已被他人修改，请刷新后重试"
        );
    }

    private BizException invalidParam(String message) {
        return new BizException(ErrorCode.INVALID_PARAM.getCode(), message);
    }
}
