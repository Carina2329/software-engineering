package com.campus.contest.module.contest.vo;

import com.campus.contest.module.contest.entity.ContestMilestoneDO;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record MilestoneVO(
        String id,
        int version,
        @JsonProperty("node_type") String nodeType,
        @JsonProperty("node_name") String nodeName,
        @JsonProperty("start_time") OffsetDateTime startTime,
        @JsonProperty("end_time") OffsetDateTime endTime,
        boolean enabled
) {
    public static MilestoneVO from(ContestMilestoneDO milestone) {
        return new MilestoneVO(
                String.valueOf(milestone.getId()),
                milestone.getVersion(),
                milestone.getNodeType(),
                milestone.getNodeName(),
                milestone.getStartTime().atOffset(ZoneOffset.ofHours(8)),
                milestone.getEndTime().atOffset(ZoneOffset.ofHours(8)),
                Integer.valueOf(1).equals(milestone.getEnabled())
        );
    }
}
