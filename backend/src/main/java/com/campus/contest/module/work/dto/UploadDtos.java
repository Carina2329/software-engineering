package com.campus.contest.module.work.dto;

import lombok.Data;

import java.util.List;

/**
 * 分片上传相关的请求 / 响应体。
 */
public class UploadDtos {

    /** 初始化上传：EP-046 请求体 */
    @Data
    public static class InitRequest {
        private String fileName;
        private Long totalSize;
        private String sha256;
        private Integer chunkSize;
    }

    /** 初始化上传：EP-046 响应体 */
    @Data
    public static class InitResponse {
        private Long uploadId;
        private Integer totalChunks;
        private Integer chunkSize;
        /** 命中已存在文件，无需上传 */
        private boolean instant;
        private Long fileId;
    }

    /** 上传单个分片：EP-047 响应体 */
    @Data
    public static class ChunkResponse {
        private Long uploadId;
        private Integer chunkIndex;
        private Integer received;
    }

    /** 查询上传进度：EP-048 响应体 */
    @Data
    public static class ProgressResponse {
        private Long uploadId;
        private Integer status;
        private Integer totalChunks;
        private Integer uploadedChunks;
        private List<Integer> uploaded;
    }

    /** 合并分片：EP-049 请求体 */
    @Data
    public static class MergeRequest {
        private Integer version;
    }

    /** 合并分片：EP-049 响应体 */
    @Data
    public static class MergeResponse {
        private Long fileId;
        private String path;
        private Long size;
        private String sha256;
    }
}
