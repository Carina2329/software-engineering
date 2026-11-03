package com.campus.contest.module.work.dto;

import lombok.Data;

import java.util.List;

@Data
public class BatchUploadResponse {

    private List<FileUploadResult> results;
}
