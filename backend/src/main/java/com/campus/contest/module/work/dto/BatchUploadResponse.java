package com.campus.contest.module.work.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class BatchUploadResponse {

    private List<FileUploadResult> results;

    @JsonProperty("success_count")
    private Integer successCount;

    @JsonProperty("failure_count")
    private Integer failureCount;
}
