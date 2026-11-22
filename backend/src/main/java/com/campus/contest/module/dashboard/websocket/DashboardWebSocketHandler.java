package com.campus.contest.module.dashboard.websocket;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.dashboard.dto.DashboardTicket;
import com.campus.contest.module.dashboard.service.DashboardAccessService;
import com.campus.contest.module.dashboard.service.DashboardOverviewService;
import com.campus.contest.module.dashboard.service.DashboardTicketService;
import com.campus.contest.module.dashboard.vo.DashboardOverviewVO;
import com.campus.contest.module.dashboard.vo.DashboardWebSocketMessage;
import com.campus.contest.security.AuthSession;
import com.campus.contest.security.AuthSessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardWebSocketHandler extends TextWebSocketHandler {

    private static final CloseStatus INVALID_TICKET = new CloseStatus(4401, "票据或会话无效");
    private static final CloseStatus FORBIDDEN = new CloseStatus(4403, "数字大屏权限或数据范围失效");
    private static final CloseStatus SERVICE_UNAVAILABLE = new CloseStatus(1013, "数字大屏服务暂不可用");
    private static final String USER_ID_ATTRIBUTE = "dashboardUserId";
    private static final String SESSION_ID_ATTRIBUTE = "dashboardSessionId";

    private final DashboardTicketService ticketService;
    private final DashboardAccessService accessService;
    private final DashboardOverviewService overviewService;
    private final AuthSessionService authSessionService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, WebSocketSession> connections = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Optional<DashboardTicket> ticket;
        try {
            ticket = ticketService.consume(extractTicket(session.getUri()));
        } catch (BizException exception) {
            closeConnection(session, closeStatusFor(exception));
            return;
        }
        if (ticket.isEmpty()) {
            closeConnection(session, INVALID_TICKET);
            return;
        }

        try {
            if (!hasValidSession(ticket.get())) {
                closeConnection(session, INVALID_TICKET);
                return;
            }
            accessService.requireAccess(ticket.get().userId());
        } catch (BizException exception) {
            closeConnection(session, closeStatusFor(exception));
            return;
        } catch (RuntimeException exception) {
            log.warn("数字大屏连接校验失败: {}", exception.getClass().getSimpleName());
            closeConnection(session, SERVICE_UNAVAILABLE);
            return;
        }

        session.getAttributes().put(USER_ID_ATTRIBUTE, ticket.get().userId());
        session.getAttributes().put(SESSION_ID_ATTRIBUTE, ticket.get().sessionId());
        connections.put(session.getId(), session);
        try {
            String connected = objectMapper.writeValueAsString(
                    DashboardWebSocketMessage.connected(now())
            );
            sendText(session, connected);
            log.info("数字大屏连接建立: webSocketSessionId={}", session.getId());
        } catch (IOException exception) {
            log.warn("数字大屏连接确认消息发送失败: {}", exception.getClass().getSimpleName());
            closeConnection(session, CloseStatus.SERVER_ERROR);
        }
    }

    @Scheduled(fixedRate = 30_000, initialDelay = 30_000)
    public void broadcastNow() {
        DashboardOverviewVO overview;
        String payload;
        try {
            overview = overviewService.buildSnapshot();
            payload = objectMapper.writeValueAsString(DashboardWebSocketMessage.overview(overview));
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("数字大屏快照聚合或序列化失败: {}", exception.getClass().getSimpleName());
            connections.values().forEach(session -> closeConnection(session, SERVICE_UNAVAILABLE));
            return;
        }

        for (WebSocketSession session : connections.values()) {
            if (!revalidate(session)) {
                continue;
            }
            try {
                sendText(session, payload);
            } catch (IOException exception) {
                log.warn(
                        "数字大屏广播失败: webSocketSessionId={}, error={}",
                        session.getId(),
                        exception.getClass().getSimpleName()
                );
                closeConnection(session, CloseStatus.SERVER_ERROR);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        connections.remove(session.getId());
        log.info(
                "数字大屏连接关闭: webSocketSessionId={}, closeCode={}",
                session.getId(),
                status.getCode()
        );
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        connections.remove(session.getId());
        log.warn(
                "数字大屏传输异常: webSocketSessionId={}, error={}",
                session.getId(),
                exception.getClass().getSimpleName()
        );
        closeConnection(session, CloseStatus.SERVER_ERROR);
    }

    private boolean revalidate(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get(USER_ID_ATTRIBUTE);
        String sessionId = (String) session.getAttributes().get(SESSION_ID_ATTRIBUTE);
        DashboardTicket identity = new DashboardTicket(userId, sessionId);
        try {
            if (!hasValidSession(identity)) {
                closeConnection(session, INVALID_TICKET);
                return false;
            }
            accessService.requireAccess(userId);
            return true;
        } catch (BizException exception) {
            closeConnection(session, closeStatusFor(exception));
            return false;
        } catch (RuntimeException exception) {
            log.warn("数字大屏连接复核失败: {}", exception.getClass().getSimpleName());
            closeConnection(session, SERVICE_UNAVAILABLE);
            return false;
        }
    }

    private boolean hasValidSession(DashboardTicket ticket) {
        if (ticket.userId() == null || ticket.sessionId() == null) {
            return false;
        }
        Optional<AuthSession> authSession = authSessionService.find(ticket.sessionId());
        return authSession.isPresent()
                && Objects.equals(ticket.userId(), authSession.get().userId())
                && (authSession.get().refreshExpiresAt() == null
                || authSession.get().refreshExpiresAt().isAfter(clock.instant()));
    }

    private String extractTicket(URI uri) {
        if (uri == null) {
            return null;
        }
        try {
            List<String> values = UriComponentsBuilder.fromUri(uri)
                    .build()
                    .getQueryParams()
                    .get("ticket");
            return values != null && values.size() == 1 ? values.get(0) : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private OffsetDateTime now() {
        return clock.instant()
                .atZone(DashboardOverviewService.BUSINESS_ZONE)
                .toOffsetDateTime()
                .truncatedTo(ChronoUnit.SECONDS);
    }

    private CloseStatus closeStatusFor(BizException exception) {
        if (ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode() == exception.getCode()) {
            return SERVICE_UNAVAILABLE;
        }
        if (ErrorCode.FUNCTION_FORBIDDEN.getCode() == exception.getCode()
                || ErrorCode.DATA_FORBIDDEN.getCode() == exception.getCode()) {
            return FORBIDDEN;
        }
        return INVALID_TICKET;
    }

    private void sendText(WebSocketSession session, String payload) throws IOException {
        if (!session.isOpen()) {
            connections.remove(session.getId());
            return;
        }
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(payload));
            }
        }
    }

    private void closeConnection(WebSocketSession session, CloseStatus status) {
        connections.remove(session.getId());
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.close(status);
                }
            }
        } catch (IOException exception) {
            log.warn(
                    "数字大屏连接关闭失败: webSocketSessionId={}, error={}",
                    session.getId(),
                    exception.getClass().getSimpleName()
            );
        }
    }
}
