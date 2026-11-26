package com.campus.contest.module.team.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AvailableTeacherQuery {

    private String keyword;

    @Min(1)
    private Integer page;

    @Min(1)
    @Max(50)
    private Integer size;
}
