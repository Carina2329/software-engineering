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
