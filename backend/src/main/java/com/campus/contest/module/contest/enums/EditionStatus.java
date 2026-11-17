package com.campus.contest.module.contest.enums;

import java.util.Arrays;

public enum EditionStatus {
    DRAFT(0),
    REGISTERING(1),
    SUBMITTING(2),
    REVIEWING(3),
    PUBLISHING(4),
    ARCHIVED(5);

    private final int databaseValue;

    EditionStatus(int databaseValue) {
        this.databaseValue = databaseValue;
    }

    public int getDatabaseValue() {
        return databaseValue;
    }

    public boolean isVisibleToOrdinaryUser() {
        return this != DRAFT;
    }

    public static EditionStatus fromDatabaseValue(Integer value) {
        return Arrays.stream(values())
                .filter(status -> status.databaseValue == value)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未知的届次状态值: " + value));
    }

    public static EditionStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("届次状态不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "届次状态只能是 DRAFT、REGISTERING、SUBMITTING、REVIEWING、PUBLISHING 或 ARCHIVED"
            );
        }
    }
}
