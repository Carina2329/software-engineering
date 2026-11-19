package com.campus.contest.module.organization.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum ScopeType {
    GLOBAL,
    ORGANIZATION,
    PROVINCE,
    EVENT;

    public static Optional<ScopeType> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.name().equals(normalized))
                .findFirst();
    }
}
