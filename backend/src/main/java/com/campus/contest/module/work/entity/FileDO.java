package com.campus.contest.module.work.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件主表：每个文件一条记录，文件本体存在存储服务（本地磁盘 / 对象存储）。
 */
@Data
@TableName("file")
public class FileDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 原始文件名 */
    private String originalName;

    /** MIME 类型 */
    private String mimeType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 存储方式：1 本地磁盘，2 对象存储 */
    private Integer storageType;

    /** 相对存储路径 */
    private String storagePath;

    /** SHA-256 摘要，用于秒传与去重 */
    private String sha256;

    /** 上传人 */
    private Long uploaderId;

    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    private Integer deleted;
    private LocalDateTime deletedAt;
    private Integer version;
}
