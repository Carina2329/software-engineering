package com.campus.contest.module.system.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * EP-001、EP-070 共用的令牌响应数据。
 */
public record AuthTokenData(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_at") Instant expiresAt,
        AuthUserData user,
        List<String> roles,
        List<String> permissions
) {
}
