package com.campus.contest.module.system.dto;

/**
 * 审计日志写入参数。params 必须是脱敏前的结构化 JSON 或 null。
 */
public record AuditLogCommand(
        Long userId,
        String module,
        String operation,
        String requestUri,
        String requestMethod,
        String params,
        String ip,
        Integer durationMs,
        Integer result,
        String traceId
) {
}
