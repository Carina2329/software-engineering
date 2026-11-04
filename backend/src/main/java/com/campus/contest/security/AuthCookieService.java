package com.campus.contest.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 统一写入和清理 Refresh Token Cookie。
 */
@Component
public class AuthCookieService {

    public static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final boolean secure;
    private final String sameSite;
    private final Duration refreshMaxAge;

    public AuthCookieService(
            @Value("${app.auth.cookie.secure:true}") boolean secure,
            @Value("${app.auth.cookie.same-site:Lax}") String sameSite,
            @Value("${app.auth.jwt.refresh-token-ttl-seconds:604800}") long refreshTtlSeconds
    ) {
        this.secure = secure;
        this.sameSite = sameSite == null || sameSite.isBlank() ? "Lax" : sameSite;
        this.refreshMaxAge = Duration.ofSeconds(refreshTtlSeconds);
    }

    public void writeRefreshToken(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(refreshMaxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearRefreshToken(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
