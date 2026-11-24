package com.campus.contest.module.team.enums;

/**
 * 队长代办制队伍状态，数据库值固定为 0-5。
 */
public enum TeamStatus {
    DRAFT(0),
    PENDING_TEACHER(1),
    PENDING_ADMIN(2),
    APPROVED(3),
    REJECTED(4),
    WITHDRAWN(5);

    private final int databaseValue;

    TeamStatus(int databaseValue) {
        this.databaseValue = databaseValue;
    }

    public int databaseValue() {
        return databaseValue;
    }

    public boolean editable() {
        return this == DRAFT || this == REJECTED || this == WITHDRAWN;
    }

    public static TeamStatus fromDatabaseValue(Integer value) {
        if (value != null) {
            for (TeamStatus status : values()) {
                if (status.databaseValue == value) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("未知队伍状态：" + value);
    }

    public static TeamStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("队伍状态不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("队伍状态非法：" + value);
        }
    }
}
