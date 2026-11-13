package com.campus.contest.module.system.messaging;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.system.enums.EmailOtpScene;
import com.campus.contest.module.system.service.EmailOtpChallengeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitEmailOtpPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private EmailOtpChallengeStore challengeStore;
    @InjectMocks
    private RabbitEmailOtpPublisher publisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "exchange", "auth.otp.exchange");
        ReflectionTestUtils.setField(publisher, "routingKey", "auth.otp.send");
        ReflectionTestUtils.setField(publisher, "confirmTimeoutMs", 5000L);
        ReflectionTestUtils.setField(publisher, "messageTtlMs", 300000L);
    }

    @Test
    void publishFailureRollsBackChallengeAndReturns50003() {
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
        EmailOtpDispatchCommand command = command("challenge-1");

        BizException exception = assertThrows(BizException.class, () -> publisher.publish(command));

        assertEquals(ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(), exception.getCode());
        verify(challengeStore).rollbackFailedDispatch(
                "challenge-1",
                "student@example.edu",
                EmailOtpScene.LOGIN
        );
    }

    @Test
    void publisherNackRollsBackChallengeAndReturns50003() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
            return null;
        }).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
        EmailOtpDispatchCommand command = command("challenge-nack");

        BizException exception = assertThrows(BizException.class, () -> publisher.publish(command));

        assertEquals(ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(), exception.getCode());
        verify(challengeStore).rollbackFailedDispatch(
                "challenge-nack",
                "student@example.edu",
                EmailOtpScene.LOGIN
        );
    }

    @Test
    void publisherConfirmTimeoutRollsBackChallengeAndReturns50003() {
        ReflectionTestUtils.setField(publisher, "confirmTimeoutMs", 10L);
        doAnswer(invocation -> null).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
        EmailOtpDispatchCommand command = command("challenge-timeout");

        BizException exception = assertThrows(BizException.class, () -> publisher.publish(command));

        assertEquals(ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(), exception.getCode());
        verify(challengeStore).rollbackFailedDispatch(
                "challenge-timeout",
                "student@example.edu",
                EmailOtpScene.LOGIN
        );
    }

    @Test
    void invalidCommandDoesNotPublishOrRollback() {
        assertThrows(BizException.class, () -> publisher.publish(command("")));
    }

    private EmailOtpDispatchCommand command(String challengeId) {
        return new EmailOtpDispatchCommand(
                challengeId,
                "student@example.edu",
                EmailOtpScene.LOGIN,
                "123456",
                Instant.now().plusSeconds(300),
                "trace-1",
                false
        );
    }
}
