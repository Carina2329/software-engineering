package com.campus.contest.module.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoleAssignmentCreateRequest {

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("role_code")
    private String roleCode;

    @JsonProperty("scope_type")
    private String scopeType;

    @JsonProperty("scope_id")
    private String scopeId;

    @JsonProperty("valid_from")
    private LocalDateTime validFrom;

    @JsonProperty("valid_to")
    private LocalDateTime validTo;
}
