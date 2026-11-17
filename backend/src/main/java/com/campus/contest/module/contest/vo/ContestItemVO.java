package com.campus.contest.module.contest.vo;

import com.campus.contest.module.contest.entity.ContestDO;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ContestItemVO(
        String id,
        @JsonProperty("contest_name") String contestName,
        String level,
        String organizer
) {
    public static ContestItemVO from(ContestDO contest) {
        return new ContestItemVO(
                String.valueOf(contest.getId()),
                contest.getContestName(),
                contest.getLevel(),
                contest.getOrganizer()
        );
    }
}
