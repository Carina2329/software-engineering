package com.campus.contest.module.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.contest.module.dashboard.service.DashboardTicketService;
import com.campus.contest.module.dashboard.vo.DashboardTicketVO;
import com.campus.contest.module.dashboard.websocket.DashboardWebSocketHandler;
import com.campus.contest.module.system.dto.AuthorizationSnapshot;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.system.service.AuthTokenService;
import com.campus.contest.module.system.service.AuthorizationSnapshotService;
import com.campus.contest.security.AuthSessionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_DASHBOARD_LIVE_TESTS", matches = "true")
@SpringBootTest(classes = DashboardLiveTest.LiveApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DashboardLiveTest {

    private static final String TICKET_KEY_PREFIX = "dashboard:ws:ticket:";

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SysUserMapper userMapper;
    @Autowired
    private AuthorizationSnapshotService snapshotService;
    @Autowired
    private AuthTokenService authTokenService;
    @Autowired
    private AuthSessionService authSessionService;
    @Autowired
    private DashboardTicketService ticketService;
    @Autowired
    private DashboardWebSocketHandler webSocketHandler;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void realHttpRedisAndWebSocketFlow() throws Exception {
        AuthTokenService.LoginResult admin = createSession("admin_demo");
        AuthTokenService.LoginResult student = createSession("student_a");
        WebSocket webSocket = null;
        try {
            HttpHeaders headers = bearerHeaders(admin.tokenData().accessToken());
            ResponseEntity<String> overviewResponse = restTemplate.exchange(
                    "/api/v1/dashboard/overview",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            assertThat(overviewResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode overviewBody = json(overviewResponse);
            assertThat(overviewBody.path("code").asInt()).isZero();
            JsonNode restData = overviewBody.path("data");
            assertThat(restData.path("summary").path("effective_school_count").asLong()).isPositive();
            System.out.println("LIVE_REST_OVERVIEW=" + overviewResponse.getBody());

            ResponseEntity<String> forbidden = restTemplate.exchange(
                    "/api/v1/dashboard/overview",
                    HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(student.tokenData().accessToken())),
                    String.class
            );
            assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(json(forbidden).path("code").asInt()).isEqualTo(30003);
            System.out.println("LIVE_STUDENT_FORBIDDEN=httpStatus=" + forbidden.getStatusCode().value()
                    + ", code=" + json(forbidden).path("code").asInt());

            String csrf = UUID.randomUUID().toString();
            headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);
            headers.add("X-XSRF-TOKEN", csrf);
            ResponseEntity<String> ticketResponse = restTemplate.exchange(
                    "/api/v1/dashboard/ws-ticket",
                    HttpMethod.POST,
                    new HttpEntity<>(null, headers),
                    String.class
            );
            assertThat(ticketResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode ticketBody = json(ticketResponse);
            assertThat(ticketBody.path("code").asInt()).isZero();
            String ticket = ticketBody.path("data").path("ticket").asText();
            assertThat(ticket).isNotBlank();
            System.out.println("LIVE_WS_TICKET=httpStatus=" + ticketResponse.getStatusCode().value()
                    + ", code=" + ticketBody.path("code").asInt()
                    + ", expires_at=" + ticketBody.path("data").path("expires_at").asText()
                    + ", ticket=[redacted]");

            DashboardTicketVO oneShot = ticketService.create(
                    Long.valueOf(admin.tokenData().user().id()),
                    admin.sessionId()
            );
            long ttlMillis = redisTemplate.getExpire(TICKET_KEY_PREFIX + oneShot.ticket(), TimeUnit.MILLISECONDS);
            assertThat(ttlMillis).isBetween(1L, 30_000L);
            assertThat(ticketService.consume(oneShot.ticket())).isPresent();
            assertThat(ticketService.consume(oneShot.ticket())).isEmpty();
            System.out.println("LIVE_REDIS_ONE_SHOT=ttlMillis=" + ttlMillis
                    + ", firstConsume=true, secondConsume=false");

            DashboardTicketVO expiring = ticketService.create(
                    Long.valueOf(admin.tokenData().user().id()),
                    admin.sessionId()
            );
            MessageListener listener = new MessageListener(objectMapper);
            webSocket = HttpClient.newHttpClient().newWebSocketBuilder()
                    .buildAsync(
                            URI.create("ws://localhost:" + port + "/ws/dashboard?ticket=" + ticket),
                            listener
                    )
                    .get(5, TimeUnit.SECONDS);
            JsonNode connected = listener.awaitType("CONNECTED", 5, TimeUnit.SECONDS);
            assertThat(connected.path("version").asInt()).isEqualTo(1);
            assertThat(connected.path("data").isNull()).isTrue();
            System.out.println("LIVE_WS_CONNECTED=" + connected);

            MessageListener duplicateListener = new MessageListener(objectMapper);
            WebSocket duplicate = HttpClient.newHttpClient().newWebSocketBuilder()
                    .buildAsync(
                            URI.create("ws://localhost:" + port + "/ws/dashboard?ticket=" + ticket),
                            duplicateListener
                    )
                    .get(5, TimeUnit.SECONDS);
            assertThat(duplicateListener.awaitClose(5, TimeUnit.SECONDS)).isEqualTo(4401);
            System.out.println("LIVE_WS_REUSED_TICKET_CLOSE=4401");

            JsonNode pushed = listener.awaitType("DASHBOARD_OVERVIEW", 40, TimeUnit.SECONDS);
            assertThat(pushed.path("version").asInt()).isEqualTo(1);
            JsonNode pushedData = pushed.path("data");
            assertThat(withoutGeneratedAt(pushedData)).isEqualTo(withoutGeneratedAt(restData));
            System.out.println("LIVE_WS_DASHBOARD_OVERVIEW=" + pushed);

            long remainingMillis = redisTemplate.getExpire(
                    TICKET_KEY_PREFIX + expiring.ticket(),
                    TimeUnit.MILLISECONDS
            );
            if (remainingMillis >= 0) {
                Thread.sleep(remainingMillis + 250);
            }
            assertThat(redisTemplate.hasKey(TICKET_KEY_PREFIX + expiring.ticket())).isFalse();
            assertThat(ticketService.consume(expiring.ticket())).isEmpty();
            System.out.println("LIVE_REDIS_EXPIRED=keyExists=false, consume=false");

            authSessionService.revokeSession(admin.sessionId());
            webSocketHandler.broadcastNow();
            assertThat(listener.awaitClose(5, TimeUnit.SECONDS)).isEqualTo(4401);
            System.out.println("LIVE_WS_REVOKED_SESSION_CLOSE=4401");
        } finally {
            if (webSocket != null) {
                webSocket.abort();
            }
            authSessionService.revokeSession(admin.sessionId());
            authSessionService.revokeSession(student.sessionId());
        }
    }

    private AuthTokenService.LoginResult createSession(String username) {
        SysUserDO user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUserDO>().eq(SysUserDO::getUsername, username)
        );
        assertThat(user).as(username).isNotNull();
        AuthorizationSnapshot snapshot = snapshotService.loadRequired(user.getId());
        return authTokenService.createSession(user, snapshot);
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private JsonNode withoutGeneratedAt(JsonNode value) {
        ObjectNode copy = value.deepCopy();
        copy.remove("generated_at");
        return copy;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.campus.contest.module.**.mapper")
    @ComponentScan(
            basePackages = "com.campus.contest",
            excludeFilters = {
                    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.campus\\.contest\\.ContestApplication"),
                    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.campus\\.contest\\..*(Test|IT)(\\$.*)?"),
                    @ComponentScan.Filter(
                            type = FilterType.REGEX,
                            pattern = "com\\.campus\\.contest\\.common\\.idempotency\\.TestRedisIdempotencyConfig"
                    )
            }
    )
    static class LiveApplication {
    }

    private static final class MessageListener implements WebSocket.Listener {
        private final ObjectMapper objectMapper;
        private final LinkedBlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        private final CompletableFuture<Integer> closeCode = new CompletableFuture<>();
        private final StringBuilder currentMessage = new StringBuilder();

        private MessageListener(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            currentMessage.append(data);
            if (last) {
                try {
                    messages.add(objectMapper.readTree(currentMessage.toString()));
                } catch (Exception exception) {
                    closeCode.completeExceptionally(exception);
                } finally {
                    currentMessage.setLength(0);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closeCode.complete(statusCode);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closeCode.completeExceptionally(error);
        }

        private JsonNode awaitType(String type, long timeout, TimeUnit unit) throws Exception {
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            while (System.nanoTime() < deadline) {
                if (closeCode.isDone()) {
                    throw new AssertionError("WebSocket closed before " + type + ": " + closeCode.get());
                }
                JsonNode message = messages.poll(100, TimeUnit.MILLISECONDS);
                if (message != null && type.equals(message.path("type").asText())) {
                    return message;
                }
            }
            throw new AssertionError("Timed out waiting for " + type);
        }

        private int awaitClose(long timeout, TimeUnit unit) throws Exception {
            return closeCode.get(timeout, unit);
        }
    }
}
