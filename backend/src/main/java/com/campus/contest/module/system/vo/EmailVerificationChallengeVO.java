package com.campus.contest.module.system.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * EP-068、EP-071 邮件验证码挑战响应。
 */
public record EmailVerificationChallengeVO(
        @JsonProperty("challenge_id") String challengeId,
        @JsonProperty("expires_at") Instant expiresAt
) {
}
