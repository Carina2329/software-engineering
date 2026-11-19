package com.campus.contest.module.organization.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("contest_edition")
public class ContestEditionScopeDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long contestId;
    private Long ownerOrgId;
    private String level;
    private Integer year;
    private Long schoolId;
    private String provinceCode;
    private String visibility;
    private Integer status;
    @TableLogic
    private Integer deleted;
}
