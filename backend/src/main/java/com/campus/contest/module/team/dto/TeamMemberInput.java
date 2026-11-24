package com.campus.contest.module.team.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TeamMemberInput {

    @JsonProperty("member_id")
    private String memberId;

    @NotBlank
    @Size(max = 32)
    @JsonProperty("member_name")
    private String memberName;

    @NotBlank
    @JsonProperty("school_id")
    private String schoolId;

    @NotBlank
    @Size(max = 20)
    @JsonProperty("student_no")
    private String studentNo;
}
