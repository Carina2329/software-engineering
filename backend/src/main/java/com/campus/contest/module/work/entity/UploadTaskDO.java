package com.campus.contest.module.work.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上传任务表：一次大文件上传对应一条记录。
 */
@Data
@TableName("upload_task")
public class UploadTaskDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 合并完成后的文件 ID */
    private Long fileId;
    private String fileName;
    private Long totalSize;
    private Integer chunkSize;
    private Integer totalChunks;
    private Integer uploadedChunks;
    /** 客户端提供的完整文件 SHA-256，用于合并后校验 */
    private String expectedSha256;

    /** 1 上传中 2 合并中 3 完成 4 失败 */
    private Integer status;

    private LocalDateTime expireAt;
    private Long uploaderId;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    private Integer deleted;
    private LocalDateTime deletedAt;
    private Integer version;
}
