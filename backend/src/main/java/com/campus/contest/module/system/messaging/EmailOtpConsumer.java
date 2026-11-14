package com.campus.contest.module.system.messaging;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.system.service.EmailOtpChallengeStore;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;

/**
 * 认证邮件 OTP 消费者。
 *
 * <p>手动 ACK；失败时通过固定 TTL 的重试队列延迟重试，超过次数后进入 DLQ。
 * 发送前检查 challenge 当前版本、有效期、suppress 和 Redis 投递状态。</p>
 */
@Service
@RequiredArgsConstructor
public class EmailOtpConsumer {

    private final EmailOtpChallengeStore challengeStore;
    private final MailDeliveryStateService deliveryStateService;
    private final RabbitTemplate rabbitTemplate;
    private final JavaMailSender mailSender;

    @Value("${app.mail.from:}")
    private String mailFrom;

    @Value("${app.mail.queue.exchange:auth.otp.exchange}")
    private String exchange;

    @Value("${app.mail.queue.retry-routing-key}")
    private String retryRoutingKey;

    @Value("${app.mail.queue.max-retries:3}")
    private int maxRetries;

    @Value("${app.mail.queue.max-busy-rechecks:3}")
    private int maxBusyRechecks;

    @RabbitListener(queues = "${app.mail.queue.send-queue:auth.otp.send.queue}")
    public void onMessage(
            EmailOtpMessage message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) throws IOException {
        if (message == null
                || message.challengeId() == null
                || message.challengeId().isBlank()
                || message.email() == null
                || message.scene() == null
                || message.otpCode() == null
                || message.expiresAt() == null) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        MailDeliveryStateService.LeaseAcquireResult lease = null;
        try {
            if (message.suppress() || !message.expiresAt().isAfter(Instant.now())) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            if (!challengeStore.isCurrentAndActive(
                    message.challengeId(),
                    message.email(),
                    message.scene(),
                    Instant.now()
            )) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            lease = deliveryStateService.acquireLease(message.challengeId());
            if (lease.result() == MailDeliveryStateService.AcquireResult.ALREADY_SENT) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            if (lease.result() == MailDeliveryStateService.AcquireResult.BUSY) {
                // 有界延迟复查：避免原消费者崩溃时直接丢消息，也避免热循环。
                if (message.busyCheckCount() >= maxBusyRechecks) {
                    channel.basicNack(deliveryTag, false, false);
                    return;
                }
                publishBusyRecheck(message, channel, deliveryTag);
                return;
            }

            try {
                sendMail(message);
                deliveryStateService.markSent(
                        message.challengeId(),
                        lease.ownerToken(),
                        message.expiresAt()
                );
                channel.basicAck(deliveryTag, false);
            } catch (RuntimeException e) {
                safeReleaseLease(message.challengeId(), lease.ownerToken());
                publishRetry(message, channel, deliveryTag);
            }
        } catch (RuntimeException e) {
            // Redis/challenge/投递状态异常进入带 retryCount 的延迟重试，不能 requeue=true 热循环。
            safeReleaseLease(message.challengeId(), lease == null ? null : lease.ownerToken());
            publishRetry(message, channel, deliveryTag);
        }
    }

    private void sendMail(EmailOtpMessage message) {
        if (mailFrom == null || mailFrom.isBlank()) {
            throw unavailable("邮件发件人未配置");
        }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(mailFrom);
            mail.setTo(message.email());
            mail.setSubject("竞赛平台邮箱验证码");
            mail.setText("您的验证码是：" + message.otpCode()
                    + "，5 分钟内有效，请勿泄露。用途：" + message.scene().name() + "。");
            mailSender.send(mail);
        } catch (MailException e) {
            throw unavailable("邮件发送失败");
        }
    }

    private void publishBusyRecheck(
            EmailOtpMessage message,
            Channel channel,
            long deliveryTag
    ) throws IOException {
        sendToRetry(message.withBusyCheckCount(message.busyCheckCount() + 1), channel, deliveryTag);
    }

    private void publishRetry(
            EmailOtpMessage message,
            Channel channel,
            long deliveryTag
    ) throws IOException {
        int nextRetryCount = message.retryCount() + 1;
        if (nextRetryCount > maxRetries) {
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        sendToRetry(message.withRetryCount(nextRetryCount), channel, deliveryTag);
    }

    private void sendToRetry(
            EmailOtpMessage retryMessage,
            Channel channel,
            long deliveryTag
    ) throws IOException {
        try {
            rabbitTemplate.convertAndSend(exchange, retryRoutingKey, retryMessage);
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException e) {
            // 无法写入重试队列时不再 requeue=true，交给主队列 DLX。
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private void safeReleaseLease(String challengeId, String ownerToken) {
        try {
            deliveryStateService.releaseLease(challengeId, ownerToken);
        } catch (RuntimeException ignored) {
            // 租约本身有 TTL；不能在日志中输出 challenge 以外的敏感信息。
        }
    }

    private BizException unavailable(String message) {
        return new BizException(
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                message
        );
    }
}
