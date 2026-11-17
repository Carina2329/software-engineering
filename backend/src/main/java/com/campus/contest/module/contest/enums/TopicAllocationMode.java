package com.campus.contest.module.contest.enums;

public enum TopicAllocationMode {
    REVIEW,
    FIRST_COME;

    public static TopicAllocationMode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("赛题分配方式不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("赛题分配方式只能是 REVIEW 或 FIRST_COME");
        }
    }
}
