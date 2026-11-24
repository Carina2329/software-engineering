package com.campus.contest.module.team.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("team_teacher_application")
public class TeamTeacherApplicationDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long registrationId;
    private Long teacherId;
    private String status;
    private String reason;
    private LocalDateTime appliedAt;
    private LocalDateTime handledAt;
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
