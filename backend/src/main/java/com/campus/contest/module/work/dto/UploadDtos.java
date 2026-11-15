package com.campus.contest.module.work.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 分片上传相关的请求 / 响应体。
 */
public class UploadDtos {

    /** 初始化上传：EP-046 请求体 */
    @Data
    public static class InitRequest {
        @JsonProperty("file_name")
        private String fileName;
        @JsonProperty("total_size")
        private Long totalSize;
        private String sha256;
        @JsonProperty("chunk_size")
        private Integer chunkSize;
    }

    /** 初始化上传：EP-046 响应体 */
    @Data
    public static class InitResponse {
        @JsonProperty("upload_id")
        private String uploadId;
        @JsonProperty("total_chunks")
        private Integer totalChunks;
        @JsonProperty("chunk_size")
        private Integer chunkSize;
        /** 命中已存在文件，无需上传 */
        @JsonProperty("already_exists")
        private boolean instant;
        @JsonProperty("file_id")
        private String fileId;
    }

    /** 上传单个分片：EP-047 响应体 */
    @Data
    public static class ChunkResponse {
        @JsonProperty("upload_id")
        private String uploadId;
        @JsonProperty("chunk_index")
        private Integer chunkIndex;
        @JsonProperty("uploaded_chunks")
        private Integer received;
        @JsonProperty("missing_chunks")
        private List<Integer> missingChunks;
    }

    /** 查询上传进度：EP-048 响应体 */
    @Data
    public static class ProgressResponse {
        @JsonProperty("id")
        private String uploadId;
        private String status;
        @JsonProperty("total_chunks")
        private Integer totalChunks;
        @JsonProperty("uploaded_chunks")
        private Integer uploadedChunks;
        @JsonProperty("missing_chunks")
        private List<Integer> missingChunks;
        @JsonProperty("expire_at")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant expiresAt;
    }

    /** 合并分片：EP-049 请求体 */
    @Data
    public static class MergeRequest {
        private Integer version;
    }

    /** 合并分片：EP-049 响应体 */
    @Data
    public static class MergeResponse {
        @JsonProperty("upload_id")
        private String uploadId;
        @JsonProperty("file_id")
        private String fileId;
        private String status;
    }
}
