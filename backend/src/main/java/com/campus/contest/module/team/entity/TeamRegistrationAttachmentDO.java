package com.campus.contest.module.team.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("team_registration_attachment")
public class TeamRegistrationAttachmentDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long registrationId;
    private Long fileId;
    private String fileType;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private Long createdBy;
}
