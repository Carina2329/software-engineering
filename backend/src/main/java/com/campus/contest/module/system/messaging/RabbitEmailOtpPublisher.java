package com.campus.contest.module.system.messaging;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.system.service.EmailOtpChallengeStore;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * C 负责的 OTP 异步发布器。
 *
 * <p>只有 RabbitMQ publisher confirm 成功后才返回；失败时调用 B 提供的原子回滚能力。</p>
 */
@Service
@RequiredArgsConstructor
public class RabbitEmailOtpPublisher implements EmailOtpPublisher {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern OTP_PATTERN = Pattern.compile("^[0-9]{6}$");

    private final RabbitTemplate rabbitTemplate;
    private final EmailOtpChallengeStore challengeStore;

    @Value("${app.mail.queue.exchange:auth.otp.exchange}")
    private String exchange;

    @Value("${app.mail.queue.routing-key}")
    private String routingKey;

    @Value("${app.mail.queue.confirm-timeout-ms:5000}")
    private long confirmTimeoutMs;

    @Value("${app.mail.queue.message-ttl-ms:300000}")
    private long messageTtlMs;

    @Override
    public void publish(EmailOtpDispatchCommand command) {
        if (!valid(command)) {
            throw invalidCommand();
        }

        EmailOtpMessage message = new EmailOtpMessage(
                command.challengeId(),
                command.email(),
                command.scene(),
                command.otpCode(),
                command.expiresAt(),
                command.traceId(),
                command.suppress(),
                0,
                0
        );
        CorrelationData correlationData = new CorrelationData(command.challengeId());
        try {
            MessagePostProcessor expiration = rabbitMessage -> {
                rabbitMessage.getMessageProperties().setExpiration(String.valueOf(messageTtlMs));
                return rabbitMessage;
            };
            rabbitTemplate.convertAndSend(exchange, routingKey, message, expiration, correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("publisher confirm rejected");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            rollback(command);
            throw unavailable();
        } catch (Exception e) {
            rollback(command);
            throw unavailable();
        }
    }

    private void rollback(EmailOtpDispatchCommand command) {
        try {
            challengeStore.rollbackFailedDispatch(
                    command.challengeId(),
                    command.email(),
                    command.scene()
            );
        } catch (RuntimeException rollbackFailure) {
            throw unavailable();
        }
    }

    private boolean valid(EmailOtpDispatchCommand command) {
        return command != null
                && command.challengeId() != null
                && !command.challengeId().isBlank()
                && command.email() != null
                && EMAIL_PATTERN.matcher(command.email().trim()).matches()
                && command.scene() != null
                && command.otpCode() != null
                && OTP_PATTERN.matcher(command.otpCode()).matches()
                && command.expiresAt() != null
                && command.expiresAt().isAfter(Instant.now())
                && command.traceId() != null
                && !command.traceId().isBlank();
    }

    private BizException invalidCommand() {
        return new BizException(ErrorCode.INVALID_PARAM.getCode(), "邮件 OTP 发布命令不完整");
    }

    private BizException unavailable() {
        return new BizException(
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getMessage()
        );
    }
}
