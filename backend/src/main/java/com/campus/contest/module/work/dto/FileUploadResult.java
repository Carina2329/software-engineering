package com.campus.contest.module.work.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 批量上传中单个文件的结果。
 */
@Data
public class FileUploadResult {

    @JsonProperty("file_name")
    private String fileName;
    @JsonProperty("file_id")
    private String fileId;
    private Long size;
    private String path;
    private boolean success;
    /** 命中已有文件（SHA-256 相同） */
    private boolean duplicated;
    private String error;
}
