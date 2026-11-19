package com.campus.contest.module.organization.model;

/**
 * 经有效性和目标存在性校验后的角色范围授权。
 */
public record EffectiveGrant(
        String roleCode,
        ScopeType scopeType,
        Long scopeId
) {
}
