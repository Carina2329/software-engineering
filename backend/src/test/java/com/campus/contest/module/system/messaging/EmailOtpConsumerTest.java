package com.campus.contest.module.system.messaging;

import com.campus.contest.module.system.enums.EmailOtpScene;
import com.campus.contest.module.system.service.EmailOtpChallengeStore;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailOtpConsumerTest {

    @Mock
    private EmailOtpChallengeStore challengeStore;
    @Mock
    private MailDeliveryStateService deliveryStateService;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private Channel channel;
    @InjectMocks
    private EmailOtpConsumer consumer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(consumer, "mailFrom", "no-reply@example.com");
        ReflectionTestUtils.setField(consumer, "exchange", "auth.otp.exchange");
        ReflectionTestUtils.setField(consumer, "retryRoutingKey", "auth.otp.retry");
        ReflectionTestUtils.setField(consumer, "maxRetries", 3);
    }

    @Test
    void sendsCurrentChallengeAndAcks() throws Exception {
        EmailOtpMessage message = message("challenge-1", 0);
        when(challengeStore.isCurrentAndActive(
                eq("challenge-1"),
                eq("student@example.edu"),
                eq(EmailOtpScene.LOGIN),
                any()
        )).thenReturn(true);
        when(deliveryStateService.acquireLease("challenge-1"))
                .thenReturn(MailDeliveryStateService.AcquireResult.ACQUIRED);

        consumer.onMessage(message, channel, 10L);

        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(deliveryStateService).markSent(eq("challenge-1"), any(Instant.class));
        verify(channel).basicAck(10L, false);
    }

    @Test
    void duplicateSentMessageIsAcknowledgedWithoutSending() throws Exception {
        EmailOtpMessage message = message("challenge-1", 0);
        when(challengeStore.isCurrentAndActive(
                eq("challenge-1"),
                eq("student@example.edu"),
                eq(EmailOtpScene.LOGIN),
                any()
        )).thenReturn(true);
        when(deliveryStateService.acquireLease("challenge-1"))
                .thenReturn(MailDeliveryStateService.AcquireResult.ALREADY_SENT);

        consumer.onMessage(message, channel, 11L);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(channel).basicAck(11L, false);
    }

    @Test
    void mailFailurePublishesRetryAndAcksOriginalMessage() throws Exception {
        EmailOtpMessage message = message("challenge-1", 0);
        when(challengeStore.isCurrentAndActive(
                eq("challenge-1"),
                eq("student@example.edu"),
                eq(EmailOtpScene.LOGIN),
                any()
        )).thenReturn(true);
        when(deliveryStateService.acquireLease("challenge-1"))
                .thenReturn(MailDeliveryStateService.AcquireResult.ACQUIRED);
        doThrow(new MailSendException("mail down"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        consumer.onMessage(message, channel, 12L);

        verify(deliveryStateService).releaseLease("challenge-1");
        verify(rabbitTemplate).convertAndSend(
                eq("auth.otp.exchange"),
                eq("auth.otp.retry"),
                any(EmailOtpMessage.class)
        );
        verify(channel).basicAck(12L, false);
    }

    @Test
    void expiredMessageIsAcknowledgedWithoutSending() throws Exception {
        EmailOtpMessage message = new EmailOtpMessage(
                "challenge-1",
                "student@example.edu",
                EmailOtpScene.LOGIN,
                "123456",
                Instant.now().minusSeconds(1),
                "trace-1",
                false,
                0
        );

        consumer.onMessage(message, channel, 13L);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(channel).basicAck(13L, false);
    }

    @Test
    void busyLeaseIsAcknowledgedWithoutCreatingAnotherRetry() throws Exception {
        EmailOtpMessage message = message("challenge-1", 0);
        when(challengeStore.isCurrentAndActive(
                eq("challenge-1"),
                eq("student@example.edu"),
                eq(EmailOtpScene.LOGIN),
                any()
        )).thenReturn(true);
        when(deliveryStateService.acquireLease("challenge-1"))
                .thenReturn(MailDeliveryStateService.AcquireResult.BUSY);

        consumer.onMessage(message, channel, 17L);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(channel).basicAck(17L, false);
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(EmailOtpMessage.class));
    }

    @Test
    void redisOrChallengeExceptionPublishesDelayedRetry() throws Exception {
        EmailOtpMessage message = message("challenge-1", 0);
        when(challengeStore.isCurrentAndActive(
                eq("challenge-1"),
                eq("student@example.edu"),
                eq(EmailOtpScene.LOGIN),
                any()
        )).thenThrow(new com.campus.contest.common.BizException(
                com.campus.contest.common.ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                "redis down"
        ));

        consumer.onMessage(message, channel, 18L);

        verify(rabbitTemplate).convertAndSend(
                eq("auth.otp.exchange"),
                eq("auth.otp.retry"),
                any(EmailOtpMessage.class)
        );
        verify(channel).basicAck(18L, false);
    }

    private EmailOtpMessage message(String challengeId, int retryCount) {
        return new EmailOtpMessage(
                challengeId,
                "student@example.edu",
                EmailOtpScene.LOGIN,
                "123456",
                Instant.now().plusSeconds(300),
                "trace-1",
                false,
                retryCount
        );
    }

    @Test
    void suppressedMessageIsAcknowledgedWithoutSending() throws Exception {
        EmailOtpMessage message = new EmailOtpMessage(
                "challenge-1",
                "student@example.edu",
                EmailOtpScene.LOGIN,
                "123456",
                Instant.now().plusSeconds(300),
                "trace-1",
                true,
                0
        );

        consumer.onMessage(message, channel, 14L);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(channel).basicAck(14L, false);
    }

    @Test
    void oldChallengeIsAcknowledgedWithoutSending() throws Exception {
        EmailOtpMessage message = message("challenge-1", 0);
        when(challengeStore.isCurrentAndActive(
                eq("challenge-1"),
                eq("student@example.edu"),
                eq(EmailOtpScene.LOGIN),
                any()
        )).thenReturn(false);

        consumer.onMessage(message, channel, 15L);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(channel).basicAck(15L, false);
    }

    @Test
    void exceededRetryLimitIsRejectedToDeadLetterQueue() throws Exception {
        EmailOtpMessage message = message("challenge-1", 3);
        when(challengeStore.isCurrentAndActive(
                eq("challenge-1"),
                eq("student@example.edu"),
                eq(EmailOtpScene.LOGIN),
                any()
        )).thenReturn(true);
        when(deliveryStateService.acquireLease("challenge-1"))
                .thenReturn(MailDeliveryStateService.AcquireResult.ACQUIRED);
        doThrow(new MailSendException("mail down"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        consumer.onMessage(message, channel, 16L);

        verify(channel).basicNack(16L, false, false);
    }
}
