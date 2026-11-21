package com.campus.contest.module.contest.enums;

public enum TeamSizeMode {
    FIXED,
    RANGE;

    public static TeamSizeMode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("队伍人数模式不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("队伍人数模式只能是 FIXED 或 RANGE");
        }
    }
}
