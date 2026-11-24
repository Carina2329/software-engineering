package com.campus.contest.module.team.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("team_member")
public class TeamMemberDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private Long editionId;
    private Long userId;
    private String memberName;
    private Long schoolId;
    private String studentNo;
    private Integer isLeader;
    private LocalDateTime joinTime;
    private Integer status;
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
