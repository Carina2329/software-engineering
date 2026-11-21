package com.campus.contest.module.dashboard.service;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.dashboard.dto.DashboardTicket;
import com.campus.contest.module.dashboard.vo.DashboardTicketVO;
import com.campus.contest.security.CurrentUserProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardTicketService {

    private static final String TICKET_KEY_PREFIX = "dashboard:ws:ticket:";
    private static final Duration TICKET_TTL = Duration.ofSeconds(30);
    private static final int TICKET_BYTES = 32;
    private static final RedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>(
            """
            local value = redis.call('GET', KEYS[1])
            if value then
                redis.call('DEL', KEYS[1])
            end
            return value
            """,
            String.class
    );

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CurrentUserProvider currentUserProvider;
    private final DashboardAccessService accessService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public DashboardTicketVO createCurrentTicket() {
        accessService.requireCurrentAccess();
        return create(
                currentUserProvider.requireUserId(),
                currentUserProvider.requireSessionId()
        );
    }

    public DashboardTicketVO create(Long userId, String sessionId) {
        byte[] randomBytes = new byte[TICKET_BYTES];
        secureRandom.nextBytes(randomBytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        Instant expiresAt = clock.instant().plus(TICKET_TTL);
        try {
            String payload = objectMapper.writeValueAsString(new DashboardTicket(userId, sessionId));
            Boolean created = redisTemplate.opsForValue().setIfAbsent(
                    ticketKey(ticket),
                    payload,
                    TICKET_TTL
            );
            if (!Boolean.TRUE.equals(created)) {
                throw unavailable();
            }
            return new DashboardTicketVO(
                    ticket,
                    expiresAt.atZone(DashboardOverviewService.BUSINESS_ZONE)
                            .toOffsetDateTime()
                            .truncatedTo(ChronoUnit.SECONDS)
            );
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("数字大屏票据写入失败: {}", exception.getClass().getSimpleName());
            throw unavailable();
        }
    }

    public Optional<DashboardTicket> consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        String payload;
        try {
            payload = redisTemplate.execute(CONSUME_SCRIPT, List.of(ticketKey(ticket)));
        } catch (DataAccessException exception) {
            log.warn("数字大屏票据消费失败: {}", exception.getClass().getSimpleName());
            throw unavailable();
        }
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        try {
            DashboardTicket value = objectMapper.readValue(payload, DashboardTicket.class);
            if (value.userId() == null || value.sessionId() == null || value.sessionId().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (JsonProcessingException exception) {
            log.warn("数字大屏票据内容无法解析");
            return Optional.empty();
        }
    }

    private String ticketKey(String ticket) {
        return TICKET_KEY_PREFIX + ticket;
    }

    private BizException unavailable() {
        return new BizException(
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                "数字大屏实时连接服务暂不可用"
        );
    }
}
