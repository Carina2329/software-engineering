package com.campus.contest.module.system;

import com.campus.contest.common.trace.TraceContext;
import com.campus.contest.module.system.dto.AuditLogCommand;
import com.campus.contest.module.system.entity.SysOperLogDO;
import com.campus.contest.module.system.mapper.SysOperLogMapper;
import com.campus.contest.module.system.service.impl.AuditLogServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditLogServiceImplTest {

    @AfterEach
    void cleanTrace() {
        TraceContext.clear();
    }

    @Test
    void redactsSensitiveValuesAndCarriesTraceId() {
        String traceId = TraceContext.startTrace();
        SysOperLogMapper mapper = mock(SysOperLogMapper.class);
        AuditLogServiceImpl service = new AuditLogServiceImpl(mapper, new ObjectMapper());
        String params = """
                {
                  "username": "student_a",
                  "password": "secret",
                  "email_code": "123456",
                  "access_token": "jwt",
                  "refresh_token": "refresh",
                  "nested": {"otp": "654321"}
                }
                """;

        service.record(new AuditLogCommand(
                1L,
                "auth",
                "LOGIN_SUCCESS",
                "/api/v1/auth/login",
                "POST",
                params,
                "127.0.0.1",
                12,
                1,
                null
        ));

        ArgumentCaptor<SysOperLogDO> captor = ArgumentCaptor.forClass(SysOperLogDO.class);
        verify(mapper).insert(captor.capture());
        SysOperLogDO log = captor.getValue();

        assertThat(log.getTraceId()).isEqualTo(traceId);
        assertThat(log.getParams()).contains("student_a", "[REDACTED]");
        assertThat(log.getParams()).doesNotContain(
                "secret", "123456", "\"jwt\"", "\"refresh\"", "654321"
        );
    }
}
