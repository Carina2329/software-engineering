package com.campus.contest.module.team.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("team_registration")
public class TeamRegistrationDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private Long editionId;
    private Integer roundNo;
    private String status;
    private String remark;
    private String membersSnapshot;
    private String eligibilitySnapshot;
    private String quotaSnapshot;
    private Integer quotaHeld;
    private Long quotaSchoolId;
    private String rejectStage;
    private String rejectReason;
    private LocalDateTime submittedAt;
    private LocalDateTime withdrawnAt;
    private LocalDateTime decidedAt;
    private Long adminReviewedBy;
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
