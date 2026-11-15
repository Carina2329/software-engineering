package com.campus.contest.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_oper_log")
public class SysOperLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String module;
    private String operation;
    private String requestUri;
    private String requestMethod;
    private String params;
    private String ip;
    private Integer durationMs;
    private Integer result;
    private String traceId;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @TableLogic
    private Integer deleted;
    private LocalDateTime deletedAt;
    @Version
    private Integer version;
}
