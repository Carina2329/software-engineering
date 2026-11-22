package com.campus.contest.module.dashboard.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DashboardProvinceSchoolVO(
        @JsonProperty("province_code") String provinceCode,
        @JsonProperty("province_name") String provinceName,
        @JsonProperty("school_count") long schoolCount
) {
}
