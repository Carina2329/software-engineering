package com.campus.contest.module.dashboard;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.dashboard.dto.DashboardTicket;
import com.campus.contest.module.dashboard.service.DashboardAccessService;
import com.campus.contest.module.dashboard.service.DashboardOverviewService;
import com.campus.contest.module.dashboard.service.DashboardTicketService;
import com.campus.contest.module.dashboard.vo.DashboardContestOperationsVO;
import com.campus.contest.module.dashboard.vo.DashboardLevelDistributionVO;
import com.campus.contest.module.dashboard.vo.DashboardOverviewVO;
import com.campus.contest.module.dashboard.vo.DashboardSummaryVO;
import com.campus.contest.module.dashboard.websocket.DashboardWebSocketHandler;
import com.campus.contest.security.AuthSession;
import com.campus.contest.security.AuthSessionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardWebSocketHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-21T09:30:00Z");

    private DashboardTicketService ticketService;
    private DashboardAccessService accessService;
    private DashboardOverviewService overviewService;
    private AuthSessionService authSessionService;
    private ObjectMapper objectMapper;
    private DashboardWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        ticketService = mock(DashboardTicketService.class);
        accessService = mock(DashboardAccessService.class);
        overviewService = mock(DashboardOverviewService.class);
        authSessionService = mock(AuthSessionService.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        handler = new DashboardWebSocketHandler(
                ticketService,
                accessService,
                overviewService,
                authSessionService,
                objectMapper,
                Clock.fixed(NOW, DashboardOverviewService.BUSINESS_ZONE)
        );
    }

    @Test
    void validConnectionReceivesConnectedWithoutCredentialsInMessagesOrLogs() throws Exception {
        WebSocketSession session = validSession("ws-1", "one-time-ticket");
        Logger logger = (Logger) LoggerFactory.getLogger(DashboardWebSocketHandler.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            handler.afterConnectionEstablished(session);
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }

        JsonNode message = objectMapper.readTree(sentMessages(session).get(0).getPayload());
        assertThat(message.path("type").asText()).isEqualTo("CONNECTED");
        assertThat(message.path("version").asInt()).isEqualTo(1);
        assertThat(message.path("generated_at").asText()).isEqualTo("2026-11-21T17:30:00+08:00");
        assertThat(message.path("data").isNull()).isTrue();
        assertThat(message.toString()).doesNotContain("one-time-ticket", "Bearer ", "Refresh Token");
        assertThat(logs.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneSatisfy(value -> assertThat(value)
                        .contains("one-time-ticket", "Bearer ", "Refresh Token"));
    }

    @Test
    void invalidExpiredForgedAndRepeatedTicketsClose4401() throws Exception {
        when(ticketService.consume(any())).thenReturn(Optional.empty());
        WebSocketSession first = session("ws-invalid", "invalid");
        WebSocketSession second = session("ws-reused", "reused");

        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);

        assertCloseCode(first, 4401);
        assertCloseCode(second, 4401);
        verify(first, never()).sendMessage(any());
        verify(second, never()).sendMessage(any());
    }

    @Test
    void permissionOrScopeFailureCloses4403() throws Exception {
        WebSocketSession session = validSession("ws-forbidden", "forbidden-ticket");
        doThrow(new BizException(ErrorCode.DATA_FORBIDDEN.getCode(), "非 GLOBAL"))
                .when(accessService).requireAccess(1L);

        handler.afterConnectionEstablished(session);

        assertCloseCode(session, 4403);
        verify(session, never()).sendMessage(any());
    }

    @Test
    void redisFailureDuringHandshakeCloses1013() throws Exception {
        WebSocketSession session = session("ws-unavailable", "ticket");
        when(ticketService.consume("ticket")).thenThrow(new BizException(
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                "Redis 不可用"
        ));

        handler.afterConnectionEstablished(session);

        assertCloseCode(session, 1013);
    }

    @Test
    void broadcastUsesExactlyTheRestSnapshotStructure() throws Exception {
        WebSocketSession session = validSession("ws-broadcast", "broadcast-ticket");
        handler.afterConnectionEstablished(session);
        DashboardOverviewVO overview = overview();
        when(overviewService.buildSnapshot()).thenReturn(overview);

        handler.broadcastNow();

        List<TextMessage> messages = sentMessages(session);
        JsonNode envelope = objectMapper.readTree(messages.get(1).getPayload());
        assertThat(envelope.path("type").asText()).isEqualTo("DASHBOARD_OVERVIEW");
        assertThat(envelope.path("version").asInt()).isEqualTo(1);
        assertThat(envelope.path("generated_at").asText()).isEqualTo("2026-11-21T17:30:00+08:00");
        assertThat(envelope.path("data")).isEqualTo(objectMapper.readTree(
                objectMapper.writeValueAsString(overview)
        ));
        verify(overviewService).buildSnapshot();
    }

    @Test
    void revokedPermissionClosesExistingConnection() throws Exception {
        WebSocketSession session = validSession("ws-revoked", "revoked-ticket");
        handler.afterConnectionEstablished(session);
        when(overviewService.buildSnapshot()).thenReturn(overview());
        doThrow(new BizException(ErrorCode.FUNCTION_FORBIDDEN.getCode(), "权限撤销"))
                .when(accessService).requireAccess(1L);

        handler.broadcastNow();

        assertCloseCode(session, 4403);
        verify(session, org.mockito.Mockito.times(1)).sendMessage(any());
    }

    @Test
    void revokedAuthenticationSessionCloses4401() throws Exception {
        WebSocketSession session = validSession("ws-session-revoked", "session-ticket");
        handler.afterConnectionEstablished(session);
        when(authSessionService.find("session-1")).thenReturn(Optional.empty());
        when(overviewService.buildSnapshot()).thenReturn(overview());

        handler.broadcastNow();

        assertCloseCode(session, 4401);
    }

    @Test
    void aggregationFailureClosesConnections1013() throws Exception {
        WebSocketSession session = validSession("ws-aggregate-error", "aggregate-ticket");
        handler.afterConnectionEstablished(session);
        when(overviewService.buildSnapshot()).thenThrow(new IllegalStateException("database offline"));

        handler.broadcastNow();

        assertCloseCode(session, 1013);
    }

    private WebSocketSession validSession(String id, String ticket) {
        WebSocketSession session = session(id, ticket);
        when(ticketService.consume(ticket)).thenReturn(Optional.of(new DashboardTicket(1L, "session-1")));
        when(authSessionService.find("session-1")).thenReturn(Optional.of(new AuthSession(
                "session-1",
                1L,
                "admin",
                "管理员",
                "admin@example.com",
                Set.of("ADMIN"),
                Set.of("dashboard:view"),
                Set.of("ALL"),
                "refresh-hash",
                NOW.plusSeconds(3600),
                NOW.minusSeconds(60)
        )));
        return session;
    }

    private WebSocketSession session(String id, String ticket) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/dashboard?ticket=" + ticket));
        when(session.getAttributes()).thenReturn(new HashMap<>());
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private List<TextMessage> sentMessages(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, org.mockito.Mockito.atLeastOnce()).sendMessage(messages.capture());
        return messages.getAllValues();
    }

    private void assertCloseCode(WebSocketSession session, int code) throws Exception {
        ArgumentCaptor<CloseStatus> status = ArgumentCaptor.forClass(CloseStatus.class);
        verify(session).close(status.capture());
        assertThat(status.getValue().getCode()).isEqualTo(code);
    }

    private DashboardOverviewVO overview() {
        return new DashboardOverviewVO(
                OffsetDateTime.parse("2026-11-21T17:30:00+08:00"),
                DashboardOverviewService.DATA_SOURCE,
                new DashboardSummaryVO(4, 9, 6, 0),
                new DashboardContestOperationsVO(2, 6, List.of(
                        new DashboardLevelDistributionVO("SCHOOL", 3),
                        new DashboardLevelDistributionVO("PROVINCE", 2),
                        new DashboardLevelDistributionVO("NATIONAL", 1)
                )),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
