package com.campus.contest.common.idempotency;

/**
 * 幂等记录的作用域，防止不同用户或会话之间互相读取结果。
 */
public record IdempotencyScope(
        PrincipalType principalType,
        String principalId
) {

    public enum PrincipalType {
        USER,
        SESSION
    }
}
