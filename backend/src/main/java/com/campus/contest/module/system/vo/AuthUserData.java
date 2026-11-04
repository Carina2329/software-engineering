package com.campus.contest.module.system.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 登录响应中的用户摘要。
 */
public record AuthUserData(
        String id,
        String username,
        @JsonProperty("real_name") String realName,
        String email
) {
}
