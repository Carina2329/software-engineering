package com.campus.contest.module.system.messaging;

import com.campus.contest.module.system.enums.EmailOtpScene;

import java.time.Instant;

/**
 * OTP 服务提交给 RabbitMQ 发布能力的跨模块命令。
 *
 * <p>OTP 只允许临时存在于内存和受控 MQ 消息中，不得进入普通日志。
 * {@link #toString()} 明确隐藏 OTP 和邮箱。</p>
 */
public record EmailOtpDispatchCommand(
        String challengeId,
        String email,
        EmailOtpScene scene,
        String otpCode,
        Instant expiresAt,
        String traceId,
        boolean suppress
) {

    @Override
    public String toString() {
        return "EmailOtpDispatchCommand{"
                + "challengeId='" + challengeId + '\''
                + ", scene=" + scene
                + ", expiresAt=" + expiresAt
                + ", traceId='" + traceId + '\''
                + ", suppress=" + suppress
                + '}';
    }
}
