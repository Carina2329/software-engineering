package com.campus.contest.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {

    @Test
    void passwordEncoderUsesCost12() {
        String hash = new SecurityConfig(null).passwordEncoder().encode("test-password");
        assertTrue(hash.startsWith("$2a$12$") || hash.startsWith("$2b$12$"));
    }
}
