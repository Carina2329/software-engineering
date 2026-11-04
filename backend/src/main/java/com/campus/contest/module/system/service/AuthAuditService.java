package com.campus.contest.module.system.service;

import com.campus.contest.module.system.dto.AuditLogCommand;
import com.campus.contest.module.system.dto.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证相关审计只记录脱敏后的登录方式、账号标识和结果。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthAuditService {

    private static final int MAX_DURATION_MS = Integer.MAX_VALUE;

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public void recordLogin(
            LoginRequest request,
            Long userId,
            String clientIp,
            int result,
            long durationNanos,
            String requestUri
    ) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (request != null && request.getLoginType() != null) {
            params.put("login_type", request.getLoginType().name());
        }
        if (request != null && request.getUsername() != null) {
            params.put("username", request.getUsername());
        }
        if (request != null && request.getEmail() != null) {
            params.put("email", request.getEmail());
        }
        record(userId, "LOGIN", clientIp, result, durationNanos, requestUri, params);
    }

    public void recordLogout(
            String clientIp,
            int result,
            long durationNanos,
            String requestUri
    ) {
        record(null, "LOGOUT", clientIp, result, durationNanos, requestUri, Map.of());
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
            log.warn("认证审计写入失败: {}", e.getClass().getSimpleName());
        } catch (Exception e) {
            log.warn("认证审计参数序列化失败: {}", e.getClass().getSimpleName());
        }
    }
}
