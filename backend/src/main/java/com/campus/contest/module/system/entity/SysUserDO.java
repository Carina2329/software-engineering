package com.campus.contest.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUserDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String realName;
    private String passwordHash;
    private String email;
    private String studentNo;
    private String college;
    private String major;
    private String grade;
    private String skillTags;
    private String title;
    private String researchArea;
    private Integer maxTeams;
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
