package com.campus.contest.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthCookieServiceTest {

    @Test
    void refreshCookieIsHttpOnlySecureAndScopedToAuthPath() {
        AuthCookieService cookies = new AuthCookieService(true, "Lax", 604800);
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookies.writeRefreshToken(response, "session.token");

        String header = response.getHeader("Set-Cookie");
        assertTrue(header.contains("refresh_token=session.token"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("Secure"));
        assertTrue(header.contains("SameSite=Lax"));
        assertTrue(header.contains("Path=/api/v1/auth"));
        assertTrue(header.contains("Max-Age=604800"));
    }

    @Test
    void clearRefreshCookieUsesZeroMaxAge() {
        AuthCookieService cookies = new AuthCookieService(false, "Lax", 604800);
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookies.clearRefreshToken(response);

        String header = response.getHeader("Set-Cookie");
        assertTrue(header.contains("Max-Age=0"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("Path=/api/v1/auth"));
    }
}
