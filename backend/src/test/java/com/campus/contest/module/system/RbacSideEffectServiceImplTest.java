package com.campus.contest.module.system;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.system.dto.AuditLogCommand;
import com.campus.contest.module.system.service.AuditLogService;
import com.campus.contest.module.system.service.impl.RbacSideEffectServiceImpl;
import com.campus.contest.security.SessionRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 会话撤销重试与 503 降级单元测试。
 */
@ExtendWith(MockitoExtension.class)
class RbacSideEffectServiceImplTest {

    @Mock
    private AuditLogService auditLogService;
    @Mock
    private SessionRevocationService sessionRevocationService;

    private RbacSideEffectServiceImpl sideEffectService;

    @BeforeEach
    void setUp() {
        sideEffectService = new RbacSideEffectServiceImpl(auditLogService, sessionRevocationService);
    }

    @Test
    @DisplayName("会话撤销成功时不写失败审计")
    void revokeSuccessDoesNotWriteFailureAudit() {
        sideEffectService.revokeSessionsOfUsers(List.of(11L, 12L), "ROLE_AUTHORIZE");

        verify(sessionRevocationService).revokeAllSessions(11L);
        verify(sessionRevocationService).revokeAllSessions(12L);
        verify(auditLogService, times(0)).record(any());
    }

    @Test
    @DisplayName("会话撤销连续失败时重试三次，最终返回 503 + 50003 并留下待重试审计")
    void revokeFailureFallsBackToServiceUnavailable() {
        doThrow(new BizException(ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(), "Redis 不可用"))
                .when(sessionRevocationService).revokeAllSessions(anyLong());

        assertThatThrownBy(() -> sideEffectService.revokeSessionsOfUsers(List.of(11L), "ROLE_AUTHORIZE"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode())
                                .isEqualTo(ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode()));

        verify(sessionRevocationService, times(3)).revokeAllSessions(11L);

        ArgumentCaptor<AuditLogCommand> captor = ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogService, times(3)).record(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(command -> {
            assertThat(command.operation()).isEqualTo("SESSION_REVOCATION_FAILED");
            assertThat(command.module()).isEqualTo("RBAC");
        });
    }

    @Test
    @DisplayName("审计写入异常不能打断业务返回")
    void auditFailureIsSwallowed() {
        doThrow(new RuntimeException("db down")).when(auditLogService).record(any());

        sideEffectService.audit(1L, "ROLE_CREATE", "{}", 0);
    }
}
