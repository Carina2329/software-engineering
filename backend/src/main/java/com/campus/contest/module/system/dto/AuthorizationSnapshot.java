package com.campus.contest.module.system.dto;

import java.util.Set;

/**
 * 登录用户的有效角色、权限和数据范围快照。
 */
public record AuthorizationSnapshot(
        Long userId,
        String username,
        Integer status,
        Set<String> roleCodes,
        Set<String> permissionCodes,
        Set<String> dataScopes
) {
}
