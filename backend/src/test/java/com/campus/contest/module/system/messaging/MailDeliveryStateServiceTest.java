package com.campus.contest.module.system.messaging;

import com.campus.contest.common.BizException;
import com.campus.contest.module.system.enums.EmailOtpScene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailDeliveryStateServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private MailDeliveryStateService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        service = new MailDeliveryStateService(redis);
    }

    @Test
    void returnsAlreadySentWhenSentKeyExists() {
        when(redis.hasKey("auth:otp:delivery:sent:c1")).thenReturn(true);

        assertEquals(
                MailDeliveryStateService.AcquireResult.ALREADY_SENT,
                service.acquireLease("c1")
        );
    }

    @Test
    void returnsBusyWhenAnotherConsumerHoldsLease() {
        when(redis.hasKey("auth:otp:delivery:sent:c1")).thenReturn(false);
        when(values.setIfAbsent(eq("auth:otp:delivery:lease:c1"), eq("1"), any()))
                .thenReturn(false);

        assertEquals(MailDeliveryStateService.AcquireResult.BUSY, service.acquireLease("c1"));
    }

    @Test
    void acquiresLeaseAndMarksSent() {
        when(redis.hasKey("auth:otp:delivery:sent:c1")).thenReturn(false);
        when(values.setIfAbsent(eq("auth:otp:delivery:lease:c1"), eq("1"), any()))
                .thenReturn(true);

        assertEquals(MailDeliveryStateService.AcquireResult.ACQUIRED, service.acquireLease("c1"));

        service.markSent("c1", java.time.Instant.now().plusSeconds(60));

        verify(values).set(eq("auth:otp:delivery:sent:c1"), eq("SENT"), any());
        verify(redis).delete("auth:otp:delivery:lease:c1");
    }

    @Test
    void invalidChallengeIdIsRejected() {
        assertThrows(BizException.class, () -> service.acquireLease(" "));
    }
}
