package com.campus.contest.module.system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * EP-071 找回密码申请请求。
 */
@Data
public class PasswordResetRequest {

    private String email;

    @JsonProperty("captcha_id")
    private String captchaId;

    @JsonProperty("captcha_code")
    private String captchaCode;
}
