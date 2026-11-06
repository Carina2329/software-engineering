package com.campus.contest.module.system.controller;

import com.campus.contest.common.BizException;
import com.campus.contest.common.Result;
import com.campus.contest.module.system.dto.EmailVerificationCodeRequest;
import com.campus.contest.module.system.dto.PasswordResetConfirm;
import com.campus.contest.module.system.dto.PasswordResetRequest;
import com.campus.contest.module.system.dto.RegisterRequest;
import com.campus.contest.module.system.enums.CaptchaScene;
import com.campus.contest.module.system.service.AccountAuditService;
import com.campus.contest.module.system.service.AccountService;
import com.campus.contest.module.system.service.CaptchaService;
import com.campus.contest.module.system.service.EmailOtpService;
import com.campus.contest.module.system.vo.CaptchaChallengeVO;
import com.campus.contest.module.system.vo.EmailVerificationChallengeVO;
import com.campus.contest.module.system.vo.RegisterResultVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * EP-067 图片验证码、EP-068 邮件 OTP、EP-069 注册、EP-071/072 找回密码。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthVerificationController {

    private final CaptchaService captchaService;
    private final EmailOtpService emailOtpService;
    private final AccountService accountService;
    private final AccountAuditService auditService;

    @GetMapping("/captcha")
    public Result<CaptchaChallengeVO> captcha(
            @RequestParam("scene") CaptchaScene scene,
            HttpServletRequest servletRequest
    ) {
        return Result.ok(captchaService.create(scene, servletRequest.getRemoteAddr()));
    }

    @PostMapping("/email-verification-codes")
    public ResponseEntity<Result<EmailVerificationChallengeVO>> sendEmailOtp(
            @RequestBody EmailVerificationCodeRequest request,
            HttpServletRequest servletRequest
    ) {
        EmailVerificationChallengeVO challenge = emailOtpService.send(request, servletRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Result.ok(challenge));
    }

    @PostMapping("/register")
    public ResponseEntity<Result<RegisterResultVO>> register(
            @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        long started = System.nanoTime();
        String clientIp = servletRequest.getRemoteAddr();
        try {
            RegisterResultVO result = accountService.register(request, clientIp);
            auditService.recordRegister(
                    parseUserId(result.id()),
                    result.username(),
                    request.getEmail(),
                    clientIp,
                    0,
                    System.nanoTime() - started,
                    servletRequest.getRequestURI()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(Result.ok(result));
        } catch (BizException e) {
            auditService.recordRegister(
                    null,
                    request == null ? null : request.getUsername(),
                    request == null ? null : request.getEmail(),
                    clientIp,
                    e.getCode(),
                    System.nanoTime() - started,
                    servletRequest.getRequestURI()
            );
            throw e;
        }
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Result<EmailVerificationChallengeVO>> requestPasswordReset(
            @RequestBody PasswordResetRequest request,
            HttpServletRequest servletRequest
    ) {
        long started = System.nanoTime();
        String clientIp = servletRequest.getRemoteAddr();
        try {
            EmailVerificationChallengeVO challenge = emailOtpService.requestPasswordReset(request, clientIp);
            auditService.recordPasswordResetRequest(
                    request == null ? null : request.getEmail(),
                    clientIp,
                    0,
                    System.nanoTime() - started,
                    servletRequest.getRequestURI()
            );
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Result.ok(challenge));
        } catch (BizException e) {
            auditService.recordPasswordResetRequest(
                    request == null ? null : request.getEmail(),
                    clientIp,
                    e.getCode(),
                    System.nanoTime() - started,
                    servletRequest.getRequestURI()
            );
            throw e;
        }
    }

    @PostMapping("/password-reset/confirm")
    public Result<Void> confirmPasswordReset(
            @RequestBody PasswordResetConfirm request,
            HttpServletRequest servletRequest
    ) {
        long started = System.nanoTime();
        String clientIp = servletRequest.getRemoteAddr();
        try {
            Long userId = accountService.confirmPasswordReset(request, clientIp);
            auditService.recordPasswordResetConfirm(
                    userId,
                    request == null ? null : request.getEmail(),
                    clientIp,
                    0,
                    System.nanoTime() - started,
                    servletRequest.getRequestURI()
            );
            return Result.ok();
        } catch (BizException e) {
            auditService.recordPasswordResetConfirm(
                    null,
                    request == null ? null : request.getEmail(),
                    clientIp,
                    e.getCode(),
                    System.nanoTime() - started,
                    servletRequest.getRequestURI()
            );
            throw e;
        }
    }

    private Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
