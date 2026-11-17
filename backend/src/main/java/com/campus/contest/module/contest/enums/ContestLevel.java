package com.campus.contest.module.contest.enums;

public enum ContestLevel {
    SCHOOL,
    PROVINCE,
    NATIONAL;

    public static ContestLevel parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("竞赛级别不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("竞赛级别只能是 SCHOOL、PROVINCE 或 NATIONAL");
        }
    }
}
