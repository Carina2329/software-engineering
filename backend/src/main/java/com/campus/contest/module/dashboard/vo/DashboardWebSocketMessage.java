package com.campus.contest.module.dashboard.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public record DashboardWebSocketMessage<T>(
        String type,
        int version,
        @JsonProperty("generated_at")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        OffsetDateTime generatedAt,
        T data
) {
    public static DashboardWebSocketMessage<Object> connected(OffsetDateTime generatedAt) {
        return new DashboardWebSocketMessage<>("CONNECTED", 1, generatedAt, null);
    }

    public static DashboardWebSocketMessage<DashboardOverviewVO> overview(DashboardOverviewVO overview) {
        return new DashboardWebSocketMessage<>(
                "DASHBOARD_OVERVIEW",
                1,
                overview.generatedAt(),
                overview
        );
    }
}
