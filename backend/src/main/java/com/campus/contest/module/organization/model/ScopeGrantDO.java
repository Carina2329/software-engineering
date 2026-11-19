package com.campus.contest.module.organization.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 范围授权的只读查询投影，不直接作为接口响应。
 */
@Data
public class ScopeGrantDO {

    private Long assignmentId;
    private Long roleId;
    private String roleCode;
    private String dataScope;
    private String scopeType;
    private Long scopeId;
    private Integer status;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private Integer deleted;
}
