package com.campus.contest.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityCurrentUserProviderTest {

    private final SecurityCurrentUserProvider provider = new SecurityCurrentUserProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsUserIdAndSessionIdFromSecurityContext() {
        AuthenticatedUser principal = new AuthenticatedUser(
                9L, "sid-9", Set.of("STUDENT"), Set.of("competition:read"), Set.of("SELF")
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities())
        );

        assertEquals(9L, provider.requireUserId());
        assertEquals("sid-9", provider.requireSessionId());
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThrows(RuntimeException.class, provider::requireUserId);
    }
}
