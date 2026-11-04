package com.campus.contest.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTokenCodecTest {

    private final SessionTokenCodec codec = new SessionTokenCodec();

    @Test
    void refreshTokenContainsOnlyLookupSessionIdAndCanBeHashed() {
        String sessionId = codec.newSessionId();
        String refreshToken = codec.newRefreshToken(sessionId);

        assertEquals(sessionId, codec.extractSessionId(refreshToken).orElseThrow());
        assertTrue(codec.matches(refreshToken, codec.hash(refreshToken)));
        assertFalse(codec.matches(refreshToken + "x", codec.hash(refreshToken)));
        assertNotEquals(refreshToken, codec.hash(refreshToken));
    }

    @Test
    void malformedRefreshTokenIsRejected() {
        assertTrue(codec.extractSessionId(null).isEmpty());
        assertTrue(codec.extractSessionId("").isEmpty());
        assertTrue(codec.extractSessionId("missing-separator").isEmpty());
        assertTrue(codec.extractSessionId(".secret").isEmpty());
        assertTrue(codec.extractSessionId("session.").isEmpty());
        assertTrue(codec.extractSessionId("a.b.c").isEmpty());
    }
}
