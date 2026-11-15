package com.campus.contest.module.system.service.impl;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.system.dto.AuditLogCommand;
import com.campus.contest.module.system.service.AuditLogService;
import com.campus.contest.module.system.service.RbacSideEffectService;
import com.campus.contest.security.SessionRevocationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;

/**
 * RBAC 审计与会话撤销实现。
 *
 * 顺序严格遵循 contract.md 第九节：MySQL 事务提交后再撤销 Redis 会话；
 * 撤销失败重试有限次，仍失败时以 50003/HTTP 503 返回并写入待重试审计记录。
 *
 * 关键修正：某个用户撤销失败后不能直接中断，否则后面的目标用户旧会话会继续有效。
 * 现在的策略是“尽力撤销全部目标用户，最后统一以 503 报告失败”，保证不会有漏网会话。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbacSideEffectServiceImpl implements RbacSideEffectService {

    private static final int REVOKE_MAX_ATTEMPTS = 3;
    private static final String MODULE = "RBAC";
    private static final String SESSION_REVOCATION_FAILED = "SESSION_REVOCATION_FAILED";

    private final AuditLogService auditLogService;
    private final SessionRevocationService sessionRevocationService;

    @Override
    public void audit(Long operatorId, String operation, String params, Integer result) {
        try {
            auditLogService.record(new AuditLogCommand(
                    operatorId,
                    MODULE,
                    operation,
                    requestUri(),
                    requestMethod(),
                    params,
                    clientIp(),
                    0,
                    result == null ? 0 : result,
                    null
            ));
        } catch (RuntimeException e) {
            // 审计失败不改变已提交的业务结果，只记录告警
            log.warn("RBAC 审计写入失败: operation={}, error={}", operation, e.getClass().getSimpleName());
        }
    }

    @Override
    public void revokeSessionsOfUsers(Collection<Long> userIds, String operation) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Collection<Long> targets = new LinkedHashSet<>(userIds);
        targets.removeIf(Objects::isNull);
        if (targets.isEmpty()) {
            return;
        }

        boolean anyFailure = false;
        Long lastFailedUserId = null;
        for (Long userId : targets) {
            if (!revokeWithRetry(userId, operation)) {
                anyFailure = true;
                lastFailedUserId = userId;
            }
        }
        if (anyFailure) {
            log.error("会话撤销存在失败用户，已尽力处理其余目标：lastFailedUserId={}, operation={}",
                    lastFailedUserId, operation);
            throw sessionRevocationUnavailable();
        }
    }

    /**
     * 单个用户最多重试 REVOKE_MAX_ATTEMPTS 次；失败返回 false，由调用方汇总后统一抛 503。
     */
    private boolean revokeWithRetry(Long userId, String operation) {
        for (int attempt = 1; attempt <= REVOKE_MAX_ATTEMPTS; attempt++) {
            try {
                sessionRevocationService.revokeAllSessions(userId);
                return true;
            } catch (BizException e) {
                log.warn("会话撤销失败，用户={}，第 {} 次尝试，code={}", userId, attempt, e.getCode());
                recordRevocationFailure(userId, operation, e);
            } catch (RuntimeException e) {
                // Redis 连接类异常同样按可重试处理，不能让一个用户的异常吞掉后续用户
                log.warn("会话撤销异常，用户={}，第 {} 次尝试，error={}",
                        userId, attempt, e.getClass().getSimpleName());
                recordRevocationFailure(userId, operation, null);
            }
        }
        return false;
    }

    private void recordRevocationFailure(Long userId, String operation, BizException failure) {
        try {
            auditLogService.record(new AuditLogCommand(
                    userId,
                    MODULE,
                    SESSION_REVOCATION_FAILED,
                    requestUri(),
                    requestMethod(),
                    "{\"operation\":\"" + operation + "\",\"state\":\"pending_retry\"}",
                    clientIp(),
                    0,
                    failure == null
                            ? ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode()
                            : failure.getCode(),
                    null
            ));
        } catch (RuntimeException e) {
            log.warn("会话撤销失败审计写入失败: {}", e.getClass().getSimpleName());
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String requestUri() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getRequestURI();
    }

    private String requestMethod() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getMethod();
    }

    /** 与各 Controller 取 clientIp 的方式保持一致；非 Web 上下文返回 null */
    @Override
    public String clientIp() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getRemoteAddr();
    }

    private BizException sessionRevocationUnavailable() {
        return new BizException(
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                "角色或权限已更新，但会话撤销失败，请稍后重试"
        );
    }
}
