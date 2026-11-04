package com.campus.contest.module.system.dto;

import com.campus.contest.module.system.enums.LoginType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * EP-001 统一登录请求。两种模式共用一个入口，由 login_type 区分。
 */
@Data
public class LoginRequest {

    @JsonProperty("login_type")
    private LoginType loginType;

    private String username;

    private String password;

    @JsonProperty("captcha_id")
    private String captchaId;

    @JsonProperty("captcha_code")
    private String captchaCode;

    private String email;

    @JsonProperty("challenge_id")
    private String challengeId;

    @JsonProperty("email_code")
    private String emailCode;
}
