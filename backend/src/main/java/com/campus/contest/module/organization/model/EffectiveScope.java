package com.campus.contest.module.organization.model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 当前用户经过目标存在性和有效期校验后的数据范围。
 */
public record EffectiveScope(
        boolean global,
        Set<Long> organizationIds,
        Set<Long> provinceCodes,
        Set<Long> eventIds
) {

    public EffectiveScope {
        organizationIds = organizationIds == null
                ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(organizationIds));
        provinceCodes = provinceCodes == null
                ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(provinceCodes));
        eventIds = eventIds == null
                ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(eventIds));
    }

    public static EffectiveScope empty() {
        return new EffectiveScope(false, Set.of(), Set.of(), Set.of());
    }
}
