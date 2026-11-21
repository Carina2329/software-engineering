package com.campus.contest.module.dashboard;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.dashboard.dto.DashboardTicket;
import com.campus.contest.module.dashboard.service.DashboardAccessService;
import com.campus.contest.module.dashboard.service.DashboardOverviewService;
import com.campus.contest.module.dashboard.service.DashboardTicketService;
import com.campus.contest.module.dashboard.vo.DashboardTicketVO;
import com.campus.contest.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardTicketServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-21T09:30:00Z");

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private CurrentUserProvider currentUserProvider;
    private DashboardAccessService accessService;
    private DashboardTicketService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        currentUserProvider = mock(CurrentUserProvider.class);
        accessService = mock(DashboardAccessService.class);
        service = new DashboardTicketService(
                redisTemplate,
                objectMapper,
                currentUserProvider,
                accessService,
                Clock.fixed(NOW, DashboardOverviewService.BUSINESS_ZONE)
        );
    }

    @Test
    void ticketExpiresAfterThirtySecondsAndStoresOnlyUserAndSession() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(30))))
                .thenReturn(true);
        when(currentUserProvider.requireUserId()).thenReturn(7L);
        when(currentUserProvider.requireSessionId()).thenReturn("session-7");

        DashboardTicketVO result = service.createCurrentTicket();

        verify(accessService).requireCurrentAccess();
        assertThat(result.ticket()).matches("[A-Za-z0-9_-]{43}");
        assertThat(result.expiresAt().toInstant()).isEqualTo(NOW.plusSeconds(30));
        assertThat(result.expiresAt().getOffset()).isEqualTo(ZoneOffset.ofHours(8));
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(key.capture(), payload.capture(), eq(Duration.ofSeconds(30)));
        assertThat(key.getValue()).isEqualTo("dashboard:ws:ticket:" + result.ticket());
        JsonNode stored = objectMapper.readTree(payload.getValue());
        assertThat(stored.path("userId").asLong()).isEqualTo(7);
        assertThat(stored.path("sessionId").asText()).isEqualTo("session-7");
        assertThat(stored.size()).isEqualTo(2);
        assertThat(payload.getValue()).doesNotContain("accessToken", "refreshToken", "password");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void ticketCanBeConsumedOnlyOnce() throws Exception {
        String payload = objectMapper.writeValueAsString(new DashboardTicket(7L, "session-7"));
        when(redisTemplate.execute(any(RedisScript.class), anyList()))
                .thenReturn(payload, null);

        Optional<DashboardTicket> first = service.consume("valid-ticket");
        Optional<DashboardTicket> repeated = service.consume("valid-ticket");

        assertThat(first).contains(new DashboardTicket(7L, "session-7"));
        assertThat(repeated).isEmpty();
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate, org.mockito.Mockito.times(2))
                .execute(any(RedisScript.class), keys.capture());
        assertThat(keys.getAllValues()).allSatisfy(value ->
                assertThat(value).containsExactly("dashboard:ws:ticket:valid-ticket"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void expiredForgedAndMissingTicketsAreRejected() {
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(null);

        assertThat(service.consume("expired")).isEmpty();
        assertThat(service.consume("forged")).isEmpty();
        assertThat(service.consume(null)).isEmpty();
        assertThat(service.consume(" ")).isEmpty();
    }

    @Test
    void redisFailureReturnsPublic503ErrorCode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(30))))
                .thenThrow(new RedisConnectionFailureException("offline"));

        assertThatThrownBy(() -> service.create(7L, "session-7"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getCode())
                            .isEqualTo(ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode());
                    assertThat(ErrorCode.resolveHttpStatus(exception.getCode()).value()).isEqualTo(503);
                });
    }
}
