package com.campus.contest.module.team.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EntryGrantBatchVO(
        @JsonProperty("upserted_count") int upsertedCount,
        @JsonProperty("revoked_count") int revokedCount
) {
}
