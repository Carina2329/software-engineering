package com.campus.contest.module.contest.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ContestPageVO(
        List<ContestItemVO> items,
        long total,
        int page,
        int size,
        @JsonProperty("has_more") boolean hasMore
) {
}
