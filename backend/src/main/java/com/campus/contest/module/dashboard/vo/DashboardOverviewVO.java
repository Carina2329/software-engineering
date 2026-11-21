package com.campus.contest.module.dashboard.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

public record DashboardOverviewVO(
        @JsonProperty("generated_at")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        OffsetDateTime generatedAt,
        @JsonProperty("data_source") String dataSource,
        DashboardSummaryVO summary,
        @JsonProperty("contest_operations") DashboardContestOperationsVO contestOperations,
        @JsonProperty("province_schools") List<DashboardProvinceSchoolVO> provinceSchools,
        @JsonProperty("user_roles") List<DashboardUserRoleVO> userRoles,
        @JsonProperty("upcoming_deadlines") List<DashboardDeadlineVO> upcomingDeadlines
) {
    public DashboardOverviewVO {
        provinceSchools = provinceSchools == null ? List.of() : List.copyOf(provinceSchools);
        userRoles = userRoles == null ? List.of() : List.copyOf(userRoles);
        upcomingDeadlines = upcomingDeadlines == null ? List.of() : List.copyOf(upcomingDeadlines);
    }
}
