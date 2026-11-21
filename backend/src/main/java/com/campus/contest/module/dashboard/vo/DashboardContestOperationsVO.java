package com.campus.contest.module.dashboard.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DashboardContestOperationsVO(
        @JsonProperty("contest_series_count") long contestSeriesCount,
        @JsonProperty("published_edition_count") long publishedEditionCount,
        @JsonProperty("level_distribution") List<DashboardLevelDistributionVO> levelDistribution
) {
    public DashboardContestOperationsVO {
        levelDistribution = levelDistribution == null ? List.of() : List.copyOf(levelDistribution);
    }
}
