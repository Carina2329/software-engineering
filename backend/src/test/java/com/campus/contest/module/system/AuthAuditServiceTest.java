package com.campus.contest.module.system;

import com.campus.contest.module.system.dto.AuditLogCommand;
import com.campus.contest.module.system.dto.LoginRequest;
import com.campus.contest.module.system.enums.LoginType;
import com.campus.contest.module.system.service.AuthAuditService;
import com.campus.contest.module.system.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthAuditServiceTest {

    @Test
    void loginAuditNeverContainsPasswordOrOtpSecrets() throws Exception {
        AuditLogService auditLogService = mock(AuditLogService.class);
        AuthAuditService auditService = new AuthAuditService(auditLogService, new ObjectMapper());
        LoginRequest request = new LoginRequest();
        request.setLoginType(LoginType.USERNAME_PASSWORD);
        request.setUsername("student_a");
        request.setPassword("StudentA#2026");
        request.setCaptchaId("captcha-id");
        request.setCaptchaCode("AB12");

        auditService.recordLogin(request, 7L, "127.0.0.1", 0, 1_500_000, "/api/v1/auth/login");

        ArgumentCaptor<AuditLogCommand> captor = ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogService).record(captor.capture());
        AuditLogCommand command = captor.getValue();
        assertEquals("AUTH", command.module());
        assertEquals("LOGIN", command.operation());
        assertEquals(7L, command.userId());
        assertEquals(1, command.durationMs());
        assertTrue(command.params().contains("student_a"));
        assertFalse(command.params().contains("StudentA#2026"));
        assertFalse(command.params().contains("AB12"));
        assertFalse(command.params().contains("captcha-id"));
    }
}
