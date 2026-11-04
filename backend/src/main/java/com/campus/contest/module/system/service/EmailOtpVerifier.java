package com.campus.contest.module.system.service;

import com.campus.contest.module.system.enums.EmailOtpScene;

/**
 * 供认证和账号流程共享的邮件 OTP 校验能力。
 */
public interface EmailOtpVerifier {

    /**
     * 校验挑战、邮箱、验证码和场景。
     * 成功时消费验证码；失败时抛出统一业务异常。
     */
    void verify(
            String challengeId,
            String email,
            String emailCode,
            EmailOtpScene scene
    );
}
