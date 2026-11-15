package com.campus.contest.module.system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * EP-069 注册请求。
 */
@Data
public class RegisterRequest {

    private String username;

    @JsonProperty("real_name")
    private String realName;

    private String password;

    private String email;

    @JsonProperty("challenge_id")
    private String challengeId;

    @JsonProperty("email_code")
    private String emailCode;
}
