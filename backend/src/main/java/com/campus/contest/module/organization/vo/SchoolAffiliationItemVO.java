package com.campus.contest.module.organization.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SchoolAffiliationItemVO {

    @JsonProperty("user_id")
    private String userId;

    private String username;

    @JsonProperty("real_name")
    private String realName;

    @JsonProperty("student_no")
    private String studentNo;

    @JsonProperty("school_id")
    private String schoolId;

    @JsonProperty("school_name")
    private String schoolName;

    private String status;

    @JsonProperty("review_reason")
    private String reviewReason;

    @JsonProperty("submitted_at")
    private LocalDateTime submittedAt;

    private Integer version;
}
