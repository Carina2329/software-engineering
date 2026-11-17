package com.campus.contest.module.contest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("contest_edition")
public class ContestEditionDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long contestId;
    private String editionName;
    private Integer year;
    private Integer status;
    private LocalDateTime registerDeadline;
    private LocalDateTime workDeadline;
    private LocalDateTime reviewStart;
    private LocalDateTime publishStart;
    private LocalDateTime publishEnd;
    private Integer minTeamSize;
    private Integer maxTeamSize;
    private String teamSizeMode;
    private Integer maxTeamsPerTeacher;
    private String topicMode;
    private Integer topicReviewRequired;
    private String topicAllocationMode;
    private Integer topicChangeLimit;
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
