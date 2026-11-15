package com.campus.contest.module.system.service.impl;

import com.campus.contest.common.trace.TraceContext;
import com.campus.contest.module.system.dto.AuditLogCommand;
import com.campus.contest.module.system.entity.SysOperLogDO;
import com.campus.contest.module.system.mapper.SysOperLogMapper;
import com.campus.contest.module.system.service.AuditLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private static final int MAX_PARAMS_LENGTH = 4_000;
    private static final String REDACTED = "[REDACTED]";

    private final SysOperLogMapper operLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void record(AuditLogCommand command) {
        SysOperLogDO log = new SysOperLogDO();
        log.setUserId(command.userId());
        log.setModule(command.module());
        log.setOperation(command.operation());
        log.setRequestUri(command.requestUri());
        log.setRequestMethod(command.requestMethod());
        log.setParams(sanitize(command.params()));
        log.setIp(command.ip());
        log.setDurationMs(command.durationMs());
        log.setResult(command.result());
        log.setTraceId(
                command.traceId() == null || command.traceId().isBlank()
                        ? TraceContext.currentTraceId()
                        : command.traceId()
        );
        log.setCreatedAt(LocalDateTime.now());
        log.setCreatedBy(command.userId() == null ? 0L : command.userId());
        log.setDeleted(0);
        log.setVersion(0);
        operLogMapper.insert(log);
    }

    private String sanitize(String params) {
        if (params == null || params.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(params);
            redact(root);
            String sanitized = objectMapper.writeValueAsString(root);
            return sanitized.length() <= MAX_PARAMS_LENGTH
                    ? sanitized
                    : sanitized.substring(0, MAX_PARAMS_LENGTH);
        } catch (Exception ignored) {
            return REDACTED;
        }
    }

    private void redact(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (isSensitiveKey(entry.getKey())) {
                    objectNode.set(entry.getKey(), TextNode.valueOf(REDACTED));
                } else {
                    redact(entry.getValue());
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::redact);
        }
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("otp")
                || normalized.contains("email_code")
                || normalized.contains("access_token")
                || normalized.contains("refresh_token")
                || normalized.contains("authorization")
                || normalized.contains("cookie")
                || normalized.equals("token")
                || normalized.contains("secret");
    }
}
