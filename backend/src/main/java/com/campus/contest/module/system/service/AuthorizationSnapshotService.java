package com.campus.contest.module.system.service;

import com.campus.contest.module.system.dto.AuthorizationSnapshot;

/**
 * 认证与 RBAC 共用的只读权限快照服务。
 */
public interface AuthorizationSnapshotService {

    /**
     * 加载用户当前有效角色、权限和数据范围。
     * 用户不存在或已停用时抛出统一认证异常。
     */
    AuthorizationSnapshot loadRequired(Long userId);
}
