package com.campus.contest.module.system.service;

import com.campus.contest.module.system.dto.EmailVerificationCodeRequest;
import com.campus.contest.module.system.dto.PasswordResetRequest;
import com.campus.contest.module.system.vo.EmailVerificationChallengeVO;

/**
 * 邮件 OTP 的发送与校验能力，同时提供认证核心使用的 {@link EmailOtpVerifier} 适配。
 */
public interface EmailOtpService extends EmailOtpVerifier {

    EmailVerificationChallengeVO send(EmailVerificationCodeRequest request, String clientIp);

    EmailVerificationChallengeVO requestPasswordReset(PasswordResetRequest request, String clientIp);
}
