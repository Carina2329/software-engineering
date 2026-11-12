package com.campus.contest.module.system.messaging;

import com.campus.contest.module.system.enums.EmailOtpScene;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EmailOtpDispatchCommandTest {

    @Test
    void toStringDoesNotExposeOtpOrEmail() {
        EmailOtpDispatchCommand command = new EmailOtpDispatchCommand(
                "challenge-1",
                "student@example.edu",
                EmailOtpScene.LOGIN,
                "123456",
                Instant.parse("2026-11-12T10:00:00Z"),
                "trace-1",
                false
        );

        assertThat(command.toString())
                .contains("challenge-1", "LOGIN", "trace-1")
                .doesNotContain("student@example.edu", "123456");
    }
}
