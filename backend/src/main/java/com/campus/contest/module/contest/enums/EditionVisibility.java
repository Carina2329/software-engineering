package com.campus.contest.module.contest.enums;

import java.util.Arrays;

public enum EditionVisibility {
    DRAFT,
    UNLISTED,
    SCHOOL,
    PROVINCE,
    PUBLIC;

    public static EditionVisibility parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("可见范围不能为空");
        }
        String normalized = value.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(item -> item.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "可见范围只能是 DRAFT、UNLISTED、SCHOOL、PROVINCE 或 PUBLIC"
                ));
    }
}
