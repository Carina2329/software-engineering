package com.campus.contest.module.system.messaging;

/**
 * 认证邮件 OTP 的唯一异步发布入口。
 *
 * <p>实现必须等待 RabbitMQ publisher confirm。发布失败时必须通过
 * {@code EmailOtpChallengeStore} 原子回滚当前请求的 challenge，并抛出
 * HTTP 503 + 业务码 50003 对应的 BizException。调用方不得再次回滚。</p>
 */
public interface EmailOtpPublisher {

    void publish(EmailOtpDispatchCommand command);
}
