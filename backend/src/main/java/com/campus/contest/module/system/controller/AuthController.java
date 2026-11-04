package com.campus.contest.module.system.controller;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.Result;
import com.campus.contest.module.system.dto.LoginRequest;
import com.campus.contest.module.system.service.AuthAuditService;
import com.campus.contest.module.system.service.AuthLoginService;
import com.campus.contest.module.system.service.AuthTokenService;
import com.campus.contest.module.system.vo.AuthTokenData;
import com.campus.contest.security.AuthCookieService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * EP-001 登录、EP-002 退出、EP-070 刷新。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthLoginService loginService;
    private final AuthTokenService tokenService;
    private final AuthCookieService cookieService;
    private final CsrfTokenRepository csrfTokenRepository;
    private final AuthAuditService auditService;

    @PostMapping("/login")
    public Result<AuthTokenData> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response
    ) {
        long started = System.nanoTime();
        String clientIp = servletRequest.getRemoteAddr();
        try {
            AuthTokenService.LoginResult result = loginService.login(request, clientIp);
            auditService.recordLogin(
                    request,
                    parseUserId(result.tokenData().user().id()),
                    clientIp,
                    0,
                    System.nanoTime() - started,
                    servletRequest.getRequestURI()
            );
            cookieService.writeRefreshToken(response, result.refreshToken());
            refreshCsrfCookie(servletRequest, response);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            return Result.ok(result.tokenData());
        } catch (BizException e) {
            auditService.recordLogin(
                    request,
                    null,
                    clientIp,
                    e.getCode(),
                    System.nanoTime() - started,
                    servletRequest.getRequestURI()
            );
            throw e;
        }
    }

    @PostMapping("/refresh")
    public Result<AuthTokenData> refresh(
            @CookieValue(name = AuthCookieService.REFRESH_COOKIE_NAME, required = false)
            String refreshToken,
            HttpServletRequest servletRequest,
            HttpServletResponse response
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BizException(
                    ErrorCode.UNAUTHENTICATED.getCode(),
                    ErrorCode.UNAUTHENTICATED.getMessage()
            );
        }
        AuthTokenService.LoginResult result = tokenService.refresh(refreshToken);
        cookieService.writeRefreshToken(response, result.refreshToken());
        refreshCsrfCookie(servletRequest, response);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return Result.ok(result.tokenData());
    }

    @PostMapping("/logout")
    public Result<Void> logout(
            @CookieValue(name = AuthCookieService.REFRESH_COOKIE_NAME, required = false)
            String refreshToken,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest servletRequest,
            HttpServletResponse response
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BizException(
                    ErrorCode.UNAUTHENTICATED.getCode(),
                    ErrorCode.UNAUTHENTICATED.getMessage()
            );
        }
        long started = System.nanoTime();
        String clientIp = servletRequest.getRemoteAddr();
        try {
            tokenService.logout(refreshToken, idempotencyKey);
            auditService.recordLogout(
                    clientIp,
                    0,
                    System.nanoTime() - started,
                    servletRequest.getRequestURI()
            );
            cookieService.clearRefreshToken(response);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            return Result.ok();
        } catch (BizException e) {
            auditService.recordLogout(
                    clientIp,
                    e.getCode(),
                    System.nanoTime() - started,
                    servletRequest.getRequestURI()
            );
            throw e;
        }
    }

    private void refreshCsrfCookie(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        CsrfToken token = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(token, request, response);
    }

    private Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
