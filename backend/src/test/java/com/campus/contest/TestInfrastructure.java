package com.campus.contest;

import com.campus.contest.common.idempotency.IdempotencyScope;
import com.campus.contest.common.idempotency.IdempotencyService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 单元测试用的替换件：不连数据库的事务模板和直通式幂等执行。
 */
public final class TestInfrastructure {

    private TestInfrastructure() {
    }

    public static TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(noopTransactionManager());
    }

    public static PlatformTransactionManager noopTransactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
                // 测试替身：不连接数据库
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
                // 测试替身：不连接数据库
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
                // 测试替身：不连接数据库
            }
        };
    }

    public static IdempotencyService passthroughIdempotencyService() {
        return new IdempotencyService() {
            @Override
            public <T> T execute(
                    IdempotencyScope scope,
                    String operation,
                    String idempotencyKey,
                    Object request,
                    Duration ttl,
                    Class<T> resultType,
                    Supplier<T> action
            ) {
                return action.get();
            }
        };
    }
}
