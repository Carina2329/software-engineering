package com.campus.contest.module.system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * EP-072 找回密码确认请求。
 */
@Data
public class PasswordResetConfirm {

    private String email;

    @JsonProperty("challenge_id")
    private String challengeId;

    @JsonProperty("email_code")
    private String emailCode;

    @JsonProperty("new_password")
    private String newPassword;
}
