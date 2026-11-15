package com.campus.contest.module.system.dto;

import com.campus.contest.module.system.enums.EmailOtpScene;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * EP-068 发送邮件验证码请求。
 */
@Data
public class EmailVerificationCodeRequest {

    private String email;

    private EmailOtpScene scene;

    @JsonProperty("captcha_id")
    private String captchaId;

    @JsonProperty("captcha_code")
    private String captchaCode;
}
