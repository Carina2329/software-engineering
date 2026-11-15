package com.campus.contest.module.system.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * EP-067 图片验证码响应。
 */
public record CaptchaChallengeVO(
        @JsonProperty("captcha_id") String captchaId,
        @JsonProperty("image_base64") String imageBase64,
        @JsonProperty("expires_at") Instant expiresAt
) {
}
