package com.campus.contest.module.contest.enums;

import java.util.Arrays;

public enum EntryMode {
    OPEN,
    INVITED,
    QUALIFIED;

    public static EntryMode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("参赛资格模式不能为空");
        }
        String normalized = value.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(item -> item.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "参赛资格模式只能是 OPEN、INVITED 或 QUALIFIED"
                ));
    }
}
