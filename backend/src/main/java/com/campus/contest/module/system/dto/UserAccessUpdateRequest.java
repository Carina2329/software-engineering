package com.campus.contest.module.system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * EP-007 修改用户状态和角色请求。
 * 只提交 status 或 role_codes 中的一项或两项，version 必须携带。
 */
@Data
public class UserAccessUpdateRequest {

    @JsonProperty("status")
    private Integer status;

    @JsonProperty("role_codes")
    private List<String> roleCodes;

    @JsonProperty("version")
    private Integer version;
}
