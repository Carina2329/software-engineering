package com.campus.contest.module.contest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("contest_milestone")
public class ContestMilestoneDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long editionId;
    private String nodeType;
    private String nodeName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer enabled;
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
