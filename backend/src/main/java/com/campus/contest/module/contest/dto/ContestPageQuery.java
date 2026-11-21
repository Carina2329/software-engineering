package com.campus.contest.module.contest.dto;

import lombok.Data;

@Data
public class ContestPageQuery {
    private Integer page;
    private Integer size;
    private String sort;
    private String level;
    private String keyword;
}
