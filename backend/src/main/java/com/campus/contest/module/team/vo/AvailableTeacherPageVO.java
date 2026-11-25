package com.campus.contest.module.team.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AvailableTeacherPageVO(
        List<AvailableTeacherVO> items,
        long total,
        int page,
        int size,
        @JsonProperty("has_more") boolean hasMore
) {
}
