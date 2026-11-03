package com.campus.contest.module.work.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件分片表：每个分片一条记录，断点续传靠它判断哪些片已经上传。
 */
@Data
@TableName("file_chunk")
public class FileChunkDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long uploadId;
    private Integer chunkIndex;
    private Integer chunkSize;
    private String chunkPath;
    private String sha256;
    private Integer status;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    private Integer deleted;
    private LocalDateTime deletedAt;
    private Integer version;
}
