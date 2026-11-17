package com.campus.contest.module.contest.dto;

import lombok.Data;

@Data
public class EditionPageQuery {
    private Integer page;
    private Integer size;
    private String sort;
    private Integer year;
    private String status;
}
