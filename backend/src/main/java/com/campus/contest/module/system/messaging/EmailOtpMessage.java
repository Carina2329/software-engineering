package com.campus.contest.module.system.messaging;

import com.campus.contest.module.system.enums.EmailOtpScene;

import java.time.Instant;

/**
 * RabbitMQ 中传输的认证邮件 OTP 消息。
 *
 * <p>toString 不输出邮箱和 OTP 明文，避免消息进入普通日志时泄露敏感数据。</p>
 */
public record EmailOtpMessage(
        String challengeId,
        String email,
        EmailOtpScene scene,
        String otpCode,
        Instant expiresAt,
        String traceId,
        boolean suppress,
        int retryCount
) {

    public EmailOtpMessage withRetryCount(int nextRetryCount) {
        return new EmailOtpMessage(
                challengeId,
                email,
                scene,
                otpCode,
                expiresAt,
                traceId,
                suppress,
                nextRetryCount
        );
    }

    @Override
    public String toString() {
        return "EmailOtpMessage{"
                + "challengeId='" + challengeId + '\''
                + ", scene=" + scene
                + ", expiresAt=" + expiresAt
                + ", retryCount=" + retryCount
                + ", suppress=" + suppress
                + '}';
    }
}
