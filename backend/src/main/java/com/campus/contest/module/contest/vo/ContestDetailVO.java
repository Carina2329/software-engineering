package com.campus.contest.module.contest.vo;

import com.campus.contest.module.contest.entity.ContestDO;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ContestDetailVO(
        String id,
        @JsonProperty("contest_name") String contestName,
        String level,
        String organizer,
        String intro,
        int version,
        List<String> editions,
        @JsonProperty("series_code") String seriesCode
) {
    public static ContestDetailVO from(ContestDO contest, List<String> editions) {
        return new ContestDetailVO(
                String.valueOf(contest.getId()),
                contest.getContestName(),
                contest.getLevel(),
                contest.getOrganizer(),
                contest.getIntro(),
                contest.getVersion(),
                editions,
                contest.getSeriesCode()
        );
    }
}
