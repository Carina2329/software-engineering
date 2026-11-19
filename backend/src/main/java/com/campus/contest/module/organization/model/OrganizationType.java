package com.campus.contest.module.organization.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum OrganizationType {
    SCHOOL(true),
    PROVINCE_ORGANIZER(true),
    NATIONAL_ORGANIZER(false),
    PLATFORM(false);

    private final boolean provinceRequired;

    OrganizationType(boolean provinceRequired) {
        this.provinceRequired = provinceRequired;
    }

    public boolean provinceRequired() {
        return provinceRequired;
    }

    public static Optional<OrganizationType> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.name().equals(normalized))
                .findFirst();
    }
}
