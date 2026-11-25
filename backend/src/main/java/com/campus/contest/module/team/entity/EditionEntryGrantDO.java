package com.campus.contest.module.team.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("edition_entry_grant")
public class EditionEntryGrantDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long targetEditionId;
    private String grantType;
    private Long leaderUserId;
    private String leaderName;
    private Long schoolId;
    private String studentNo;
    private Long sourceEditionId;
    private String sourceTeamCode;
    private Long targetTeamId;
    private String status;
    private String reason;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    private LocalDateTime revokedAt;
    private Long revokedBy;
    @TableLogic
    private Integer deleted;
    @Version
    private Integer version;
}
