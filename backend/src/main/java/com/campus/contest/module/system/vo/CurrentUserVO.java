package com.campus.contest.module.system.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * EP-003 GET /api/v1/users/me 响应体。
 *
 * 字段严格对应 docs/api/openapi-v1.json 中 EP-003 的 data 定义：
 * id、username、real_name、user_type、college、major、grade、skill_tags、roles、data_scopes、permissions。
 *
 * BIZ-PROFILE-001 追加 student_no、title 与档案 version：
 * - student_no 只在学生档案返回，教师档案为 null；
 * - title 只在教师档案返回，学生档案为 null；
 * - skill_tags 保持只读兼容，本任务不提供编辑入口。
 */
@Data
public class CurrentUserVO {

    private String id;

    private String username;

    @JsonProperty("real_name")
    private String realName;

    @JsonProperty("user_type")
    private String userType;

    private String college;

    private String major;

    private String grade;

    /** 学号，仅学生档案使用；教师档案为 null */
    @JsonProperty("student_no")
    private String studentNo;

    /** 职称，仅教师档案使用；学生档案为 null */
    private String title;

    /** 档案乐观锁版本号，修改档案时必须回传 */
    private Integer version;

    @JsonProperty("skill_tags")
    private List<String> skillTags = new ArrayList<>();

    /** 角色编码 */
    private List<String> roles = new ArrayList<>();

    /** 数据范围，来自角色上的 data_scope 字段 */
    @JsonProperty("data_scopes")
    private List<String> dataScopes = new ArrayList<>();

    /**
     * 当前角色合并后的权限编码，仅供前端展示；
     * 后端授权以 Redis 会话快照与 @PreAuthorize 为准。
     */
    private List<String> permissions = new ArrayList<>();
}
