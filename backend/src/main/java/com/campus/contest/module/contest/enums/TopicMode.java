package com.campus.contest.module.contest.enums;

public enum TopicMode {
    PRESET_UNLIMITED,
    PRESET_LIMITED,
    SELF_PROPOSED,
    NO_TOPIC;

    public static TopicMode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("赛题模式不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "赛题模式只能是 PRESET_UNLIMITED、PRESET_LIMITED、SELF_PROPOSED 或 NO_TOPIC"
            );
        }
    }

    public static TopicMode parseNullable(String value) {
        return value == null || value.isBlank() ? null : parse(value);
    }
}
