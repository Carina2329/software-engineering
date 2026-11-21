package com.campus.contest.module.dashboard.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DashboardSummaryVO(
        @JsonProperty("effective_school_count") long effectiveSchoolCount,
        @JsonProperty("active_user_count") long activeUserCount,
        @JsonProperty("registering_edition_count") long registeringEditionCount,
        @JsonProperty("pending_affiliation_count") long pendingAffiliationCount
) {
}
