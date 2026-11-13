package com.campus.contest.module.system.messaging;

import com.campus.contest.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailDeliveryStateServiceTest {

    private StringRedisTemplate redis;
    private MailDeliveryStateService service;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        service = new MailDeliveryStateService(redis);
    }

    @Test
    void returnsAlreadySentWhenLuaReportsSent() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object.class), any(Object.class)))
                .thenReturn(-1L);

        MailDeliveryStateService.LeaseAcquireResult result = service.acquireLease("c1");

        assertEquals(MailDeliveryStateService.AcquireResult.ALREADY_SENT, result.result());
        assertEquals(null, result.ownerToken());
    }

    @Test
    void returnsBusyWhenLuaCannotAcquireLease() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object.class), any(Object.class)))
                .thenReturn(0L);

        assertEquals(
                MailDeliveryStateService.AcquireResult.BUSY,
                service.acquireLease("c1").result()
        );
    }

    @Test
    void acquiresLeaseWithUniqueOwnerAndMarksSentUsingOwner() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object.class), any(Object.class)))
                .thenReturn(1L);

        MailDeliveryStateService.LeaseAcquireResult first = service.acquireLease("c1");
        MailDeliveryStateService.LeaseAcquireResult second = service.acquireLease("c1");

        assertEquals(MailDeliveryStateService.AcquireResult.ACQUIRED, first.result());
        assertNotNull(first.ownerToken());
        assertNotNull(second.ownerToken());
        assertNotEquals(first.ownerToken(), second.ownerToken());

        service.markSent("c1", first.ownerToken(), Instant.now().plusSeconds(60));

        verify(redis, times(3)).execute(
                any(RedisScript.class),
                anyList(),
                any(Object.class),
                any(Object.class)
        );
    }

    @Test
    void releaseUsesCompareAndDelete() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object.class)))
                .thenReturn(1L);

        assertEquals(true, service.releaseLease("c1", "owner-1"));
    }

    @Test
    void invalidChallengeIdIsRejected() {
        assertThrows(BizException.class, () -> service.acquireLease(" "));
    }
}
