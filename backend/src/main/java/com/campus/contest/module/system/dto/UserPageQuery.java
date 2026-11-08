package com.campus.contest.module.system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * EP-005 用户列表查询条件。排序字段只接受白名单，禁止前端直接传 SQL 片段。
 */
@Data
public class UserPageQuery {

    private Integer page;

    private Integer size;

    private String sort;

    private String username;

    @JsonProperty("user_type")
    private String userType;

    private Integer status;
}
