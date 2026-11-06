package com.campus.contest.module.system.service;

import com.campus.contest.module.system.dto.AuditLogCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 注册和找回密码审计：只记录脱敏后的账号标识、结果和 IP。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountAuditService {

    private static final int MAX_DURATION_MS = Integer.MAX_VALUE;

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public void recordRegister(
            Long userId,
            String username,
            String email,
            String clientIp,
            int result,
            long durationNanos,
            String requestUri
    ) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (username != null) {
            params.put("username", username);
        }
        if (email != null) {
            params.put("email", email);
        }
        record(userId, "REGISTER", clientIp, result, durationNanos, requestUri, params);
    }

    public void recordPasswordResetRequest(
            String email,
            String clientIp,
            int result,
            long durationNanos,
            String requestUri
    ) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (email != null) {
            params.put("email", email);
        }
        record(null, "PASSWORD_RESET_REQUEST", clientIp, result, durationNanos, requestUri, params);
    }

    public void recordPasswordResetConfirm(
            Long userId,
            String email,
            String clientIp,
            int result,
            long durationNanos,
            String requestUri
    ) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (email != null) {
            params.put("email", email);
        }
        record(userId, "PASSWORD_RESET_CONFIRM", clientIp, result, durationNanos, requestUri, params);
    }

    private void record(
            Long userId,
            String operation,
            String clientIp,
            int result,
            long durationNanos,
            String requestUri,
            Map<String, Object> params
    ) {
        try {
            long durationMs = Math.max(0, durationNanos / 1_000_000);
            auditLogService.record(new AuditLogCommand(
                    userId,
                    "AUTH",
                    operation,
                    requestUri,
                    "POST",
                    objectMapper.writeValueAsString(params),
                    clientIp,
                    (int) Math.min(durationMs, MAX_DURATION_MS),
                    result,
                    null
            ));
        } catch (RuntimeException e) {
            log.warn("账号流程审计写入失败: {}", e.getClass().getSimpleName());
        } catch (Exception e) {
            log.warn("账号流程审计参数序列化失败: {}", e.getClass().getSimpleName());
        }
    }
}
