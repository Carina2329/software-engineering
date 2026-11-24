package com.campus.contest.module.team.service;

import java.util.Set;

/**
 * 当前用户在队伍列表和详情中的只读范围。
 */
public record TeamReadScope(
        boolean global,
        Long userId,
        Set<Long> organizationIds,
        Set<Long> provinceCodes,
        Set<Long> eventIds
) {
    public TeamReadScope {
        organizationIds = organizationIds == null ? Set.of() : Set.copyOf(organizationIds);
        provinceCodes = provinceCodes == null ? Set.of() : Set.copyOf(provinceCodes);
        eventIds = eventIds == null ? Set.of() : Set.copyOf(eventIds);
    }
}
