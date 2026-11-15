package com.campus.contest.common.idempotency;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 带用户或会话作用域的幂等执行入口。
 */
public interface IdempotencyService {

    <T> T execute(
            IdempotencyScope scope,
            String operation,
            String idempotencyKey,
            Object request,
            Duration ttl,
            Class<T> resultType,
            Supplier<T> action
    );
}
