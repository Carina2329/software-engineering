package com.campus.contest.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitMqConfigTest {

    @Test
    void retryQueueHasThirtySecondTtlAndDeadLettersBackToMainExchange() {
        Queue queue = new RabbitMqConfig().emailOtpRetryQueue(
                "auth.otp.retry.queue",
                "auth.otp.exchange",
                "auth.otp.send",
                30_000L
        );

        assertEquals(30_000, queue.getArguments().get("x-message-ttl"));
        assertEquals("auth.otp.exchange", queue.getArguments().get("x-dead-letter-exchange"));
    }

    @Test
    void sendQueueDeadLettersToDlq() {
        Queue queue = new RabbitMqConfig().emailOtpSendQueue(
                "auth.otp.send.queue",
                "auth.otp.dlx",
                "auth.otp.dlq"
        );

        assertEquals("auth.otp.dlx", queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals("auth.otp.dlq", queue.getArguments().get("x-dead-letter-routing-key"));
        assertTrue(queue.isDurable());
    }
}
