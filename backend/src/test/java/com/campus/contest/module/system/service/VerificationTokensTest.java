package com.campus.contest.module.system.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationTokensTest {

    @Test
    void nextIdIsUniqueAndNonBlank() {
        String first = VerificationTokens.nextId();
        String second = VerificationTokens.nextId();
        assertNotNull(first);
        assertFalse(first.isBlank());
        assertNotEquals(first, second);
    }

    @Test
    void sha256AndMatchesAreConstantTimeComparable() {
        String hash = VerificationTokens.sha256Hex("123456");
        assertTrue(VerificationTokens.matches("123456", hash));
        assertFalse(VerificationTokens.matches("654321", hash));
    }
}
