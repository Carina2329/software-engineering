package com.campus.contest.module.contest.enums;

public enum MilestoneNodeType {
    REGISTER_END,
    WORK_END,
    REVIEW,
    PUBLISH;

    public static MilestoneNodeType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("赛程节点类型不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("赛程节点类型只能是 REGISTER_END、WORK_END、REVIEW 或 PUBLISH");
        }
    }
}
