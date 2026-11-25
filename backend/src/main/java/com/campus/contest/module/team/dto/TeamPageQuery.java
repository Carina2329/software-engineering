package com.campus.contest.module.team.dto;

import lombok.Data;

@Data
public class TeamPageQuery {

    private Integer page;
    private Integer size;
    private String sort;
    private String status;
    private String topicId;
    private String teacherId;
    private String keyword;
}
