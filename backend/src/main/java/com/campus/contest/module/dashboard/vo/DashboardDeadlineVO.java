package com.campus.contest.module.dashboard.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public record DashboardDeadlineVO(
        @JsonProperty("edition_id") String editionId,
        @JsonProperty("edition_name") String editionName,
        @JsonProperty("contest_name") String contestName,
        String level,
        @JsonProperty("register_deadline")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        OffsetDateTime registerDeadline
) {
}
