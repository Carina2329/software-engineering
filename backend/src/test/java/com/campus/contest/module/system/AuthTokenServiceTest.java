package com.campus.contest.module.system;

import com.campus.contest.common.idempotency.IdempotencyScope;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.system.service.AuthTokenService;
import com.campus.contest.security.AuthSession;
import com.campus.contest.security.AuthSessionService;
import com.campus.contest.security.JwtService;
import com.campus.contest.security.SessionTokenCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

    @Mock
    private AuthSessionService sessionService;
    @Mock
    private SessionTokenCodec tokenCodec;
    @Mock
    private JwtService jwtService;
    @Mock
    private IdempotencyService idempotencyService;
    @InjectMocks
    private AuthTokenService tokenService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tokenService, "refreshTokenTtlSeconds", 604800L);
    }

    @Test
    void refreshRotatesRefreshTokenAndIssuesNewAccessJwt() {
        AuthSession session = session("sid-1", "old-hash");
        AuthSession rotated = session("sid-1", "new-hash");
        Instant expiresAt = Instant.parse("2026-11-04T10:15:00Z");
        when(tokenCodec.extractSessionId("old-refresh")).thenReturn(Optional.of("sid-1"));
        when(sessionService.find("sid-1")).thenReturn(Optional.of(session));
        when(sessionService.matchesRefreshToken(session, "old-refresh")).thenReturn(true);
        when(jwtService.issue(7L, "sid-1"))
                .thenReturn(new JwtService.IssuedJwt("new-access", expiresAt));
        when(tokenCodec.newRefreshToken("sid-1")).thenReturn("new-refresh");
        when(sessionService.rotate(session, "old-refresh", "new-refresh", Duration.ofDays(7)))
                .thenReturn(rotated);

        AuthTokenService.LoginResult result = tokenService.refresh("old-refresh");

        assertEquals("new-refresh", result.refreshToken());
        assertEquals("new-access", result.tokenData().accessToken());
        assertEquals(expiresAt, result.tokenData().expiresAt());
        verify(sessionService).rotate(session, "old-refresh", "new-refresh", Duration.ofDays(7));
    }

    @Test
    @SuppressWarnings("unchecked")
    void logoutUsesSessionScopedIdempotencyAndRevokesSession() {
        AuthSession session = session("sid-1", "hash");
        when(tokenCodec.extractSessionId("refresh")).thenReturn(Optional.of("sid-1"));
        when(sessionService.find("sid-1")).thenReturn(Optional.of(session));
        when(sessionService.matchesRefreshToken(session, "refresh")).thenReturn(true);
        when(idempotencyService.execute(
                any(IdempotencyScope.class),
                eq("auth.logout"),
                eq("idem-1"),
                any(),
                eq(Duration.ofDays(7)),
                eq(Boolean.class),
                any()
        )).thenAnswer(invocation -> {
            Supplier<Boolean> action = invocation.getArgument(6);
            return action.get();
        });

        tokenService.logout("refresh", "idem-1");

        verify(sessionService).revokeSession("sid-1");
    }

    private AuthSession session(String sessionId, String refreshHash) {
        return new AuthSession(
                sessionId,
                7L,
                "student_a",
                "学生 A",
                "student_a@example.edu",
                Set.of("STUDENT"),
                Set.of("competition:read"),
                Set.of("SELF"),
                refreshHash,
                Instant.parse("2026-11-11T10:00:00Z"),
                Instant.parse("2026-11-04T10:00:00Z")
        );
    }
}
