package com.campus.contest.module.team.service;

import com.campus.contest.module.system.dto.AuditLogCommand;
import com.campus.contest.module.system.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class TeamAuditService {

    private static final String MODULE = "TEAM";

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public void record(Long operatorId, String operation, Object params) {
        HttpServletRequest request = currentRequest();
        try {
            auditLogService.record(new AuditLogCommand(
                    operatorId,
                    MODULE,
                    operation,
                    request == null ? null : request.getRequestURI(),
                    request == null ? null : request.getMethod(),
                    objectMapper.writeValueAsString(params),
                    request == null ? null : request.getRemoteAddr(),
                    0,
                    0,
                    null
            ));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("队伍业务审计参数无法序列化", exception);
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
