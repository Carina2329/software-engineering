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
        for (Long userId : targets) {
            revokeWithRetry(userId, operation);
        }
    }

    @Override
    public String clientIp() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getRemoteAddr();
    }

    private void revokeWithRetry(Long userId, String operation) {
        for (int attempt = 1; attempt <= REVOKE_MAX_ATTEMPTS; attempt++) {
            try {
                sessionRevocationService.revokeAllSessions(userId);
                return;
            } catch (BizException e) {
                log.warn("会话撤销失败，用户={}，第 {} 次尝试，code={}", userId, attempt, e.getCode());
                recordRevocationFailure(userId, operation, e);
            }
        }
        throw sessionRevocationUnavailable();
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

    private BizException sessionRevocationUnavailable() {
        return new BizException(
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                "角色或权限已更新，但会话撤销失败，请稍后重试"
        );
    }
}
