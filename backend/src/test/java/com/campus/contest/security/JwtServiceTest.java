package com.campus.contest.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "0123456789abcdef0123456789abcdef",
            "contest-agent-platform",
            900
    );

    @Test
    void issuedJwtContainsOnlyRequiredClaims() {
        JwtService.IssuedJwt issued = jwtService.issue(42L, "session-123");
        Jwt jwt = jwtService.decode(issued.token());

        assertEquals("42", jwt.getSubject());
        assertEquals("session-123", jwt.getClaimAsString("sid"));
        assertEquals("contest-agent-platform", jwt.getClaimAsString("iss"));
        assertNotNull(jwt.getIssuedAt());
        assertNotNull(jwt.getExpiresAt());
        assertTrue(jwt.getExpiresAt().isAfter(jwt.getIssuedAt()));
        assertFalse(jwt.getClaims().containsKey("email"));
        assertFalse(jwt.getClaims().containsKey("roles"));
        assertFalse(jwt.getClaims().containsKey("permissions"));
    }

    @Test
    void tamperedJwtIsRejected() {
        String token = jwtService.issue(42L, "session-123").token();
        assertThrows(JwtException.class, () -> jwtService.decode(token + "x"));
    }
}
