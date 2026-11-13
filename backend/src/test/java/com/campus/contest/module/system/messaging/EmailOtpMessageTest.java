package com.campus.contest.module.system.messaging;

import com.campus.contest.module.system.enums.EmailOtpScene;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailOtpMessageTest {

    @Test
    void toStringDoesNotExposeEmailOrOtp() {
        EmailOtpMessage message = new EmailOtpMessage(
                "challenge-1",
                "student@example.edu",
                EmailOtpScene.LOGIN,
                "123456",
                Instant.parse("2026-11-13T10:00:00Z"),
                "trace-1",
                false,
                2
        );

        String text = message.toString();
        assertFalse(text.contains("student@example.edu"));
        assertFalse(text.contains("123456"));
        assertTrue(text.contains("challenge-1"));
        assertTrue(text.contains("retryCount=2"));
    }
}
