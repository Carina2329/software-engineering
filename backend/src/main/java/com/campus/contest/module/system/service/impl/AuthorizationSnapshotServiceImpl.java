package com.campus.contest.module.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.system.dto.AuthorizationSnapshot;
import com.campus.contest.module.system.entity.SysMenuDO;
import com.campus.contest.module.system.entity.SysRoleDO;
import com.campus.contest.module.system.entity.SysRoleMenuDO;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.entity.SysUserRoleDO;
import com.campus.contest.module.system.mapper.SysMenuMapper;
import com.campus.contest.module.system.mapper.SysRoleMapper;
import com.campus.contest.module.system.mapper.SysRoleMenuMapper;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.system.mapper.SysUserRoleMapper;
import com.campus.contest.module.system.service.AuthorizationSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthorizationSnapshotServiceImpl implements AuthorizationSnapshotService {

    private static final int USER_ACTIVE = 1;
    private static final int ROLE_GRANT_ACTIVE = 1;
    private static final int NOT_DELETED = 0;
    private static final int RESOURCE_ENABLED = 1;

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    @Override
    public AuthorizationSnapshot loadRequired(Long userId) {
        SysUserDO user = userId == null ? null : userMapper.selectById(userId);
        if (user == null || !Objects.equals(USER_ACTIVE, user.getStatus())) {
            throw new BizException(ErrorCode.UNAUTHENTICATED.getCode(), "账号或凭证不正确");
        }

        LocalDateTime now = LocalDateTime.now();
        List<SysUserRoleDO> storedUserRoles = userRoleMapper.selectList(
                Wrappers.<SysUserRoleDO>lambdaQuery()
                        .eq(SysUserRoleDO::getUserId, userId)
                        .eq(SysUserRoleDO::getStatus, ROLE_GRANT_ACTIVE)
                        .and(validFrom -> validFrom.isNull(SysUserRoleDO::getValidFrom)
                                .or()
                                .le(SysUserRoleDO::getValidFrom, now))
                        .and(validTo -> validTo.isNull(SysUserRoleDO::getValidTo)
                                .or()
                                .gt(SysUserRoleDO::getValidTo, now))
        );
        List<SysUserRoleDO> userRoles = storedUserRoles == null
                ? List.of()
                : storedUserRoles.stream()
                        .filter(roleGrant -> isEffective(roleGrant, now))
                        .toList();
        Set<Long> roleIds = userRoles.stream()
                .map(SysUserRoleDO::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<SysRoleDO> roles = roleIds.isEmpty() ? List.of() : roleMapper.selectBatchIds(roleIds);
        Set<String> roleCodes = mappedValues(roles, SysRoleDO::getRoleCode);
        Set<String> dataScopes = mappedValues(roles, SysRoleDO::getDataScope);

        List<SysRoleMenuDO> roleMenus = roleIds.isEmpty()
                ? List.of()
                : roleMenuMapper.selectList(
                        Wrappers.<SysRoleMenuDO>lambdaQuery().in(SysRoleMenuDO::getRoleId, roleIds)
                );
        Set<Long> menuIds = roleMenus.stream()
                .map(SysRoleMenuDO::getMenuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<SysMenuDO> menus = menuIds.isEmpty() ? List.of() : menuMapper.selectBatchIds(menuIds);
        Set<String> permissions = menus.stream()
                .filter(menu -> Objects.equals(RESOURCE_ENABLED, menu.getStatus()))
                .map(SysMenuDO::getPerms)
                .filter(Objects::nonNull)
                .filter(perms -> !perms.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new AuthorizationSnapshot(
                user.getId(),
                user.getUsername(),
                user.getStatus(),
                roleCodes,
                permissions,
                dataScopes
        );
    }

    private <T> Set<String> mappedValues(
            Collection<T> values,
            java.util.function.Function<T, String> mapper
    ) {
        return values.stream()
                .map(mapper)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isEffective(SysUserRoleDO roleGrant, LocalDateTime now) {
        return roleGrant != null
                && Objects.equals(ROLE_GRANT_ACTIVE, roleGrant.getStatus())
                && Objects.equals(NOT_DELETED, roleGrant.getDeleted())
                && (roleGrant.getValidFrom() == null || !roleGrant.getValidFrom().isAfter(now))
                && (roleGrant.getValidTo() == null || roleGrant.getValidTo().isAfter(now));
    }
}
