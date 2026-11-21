package com.campus.contest.module.contest.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ContestCreateVO(
        String id,
        @JsonProperty("contest_name") String contestName
) {
}
