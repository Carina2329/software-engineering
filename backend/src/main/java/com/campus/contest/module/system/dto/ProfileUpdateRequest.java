package com.campus.contest.module.system.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * EP-004 PATCH /api/v1/users/me 请求体。
 *
 * 只暴露个人档案冻结字段：real_name、student_no、college、major、grade、title 和 version。
 * username、password_hash、email、status、角色和权限都不在本 DTO 中声明，
 * 一旦客户端提交这些字段，会被 {@link #collectUnexpectedField(String, Object)} 收集，
 * 由 Service 统一返回 400 + 10001，避免“字段被静默忽略”造成越权假象。
 */
@Data
public class ProfileUpdateRequest {

    @JsonProperty("real_name")
    private String realName;

    @JsonProperty("student_no")
    private String studentNo;

    @JsonProperty("college")
    private String college;

    @JsonProperty("major")
    private String major;

    @JsonProperty("grade")
    private String grade;

    @JsonProperty("title")
    private String title;

    @JsonProperty("version")
    private Integer version;

    /** 请求体中出现但不在档案白名单内的字段名，仅用于拒绝越界修改 */
    @JsonIgnore
    private List<String> unexpectedFields = new ArrayList<>();

    @JsonAnySetter
    public void collectUnexpectedField(String name, Object value) {
        unexpectedFields.add(name);
    }
}
