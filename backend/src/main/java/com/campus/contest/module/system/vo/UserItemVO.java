package com.campus.contest.module.system.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * EP-005 用户列表项。user_type 由用户当前角色推导，库内没有独立用户类型字段。
 */
@Data
public class UserItemVO {

    private String id;

    private String username;

    @JsonProperty("real_name")
    private String realName;

    @JsonProperty("user_type")
    private String userType;

    private Integer status;

    @JsonProperty("role_codes")
    private List<String> roleCodes = new ArrayList<>();

    private Integer version;
}
