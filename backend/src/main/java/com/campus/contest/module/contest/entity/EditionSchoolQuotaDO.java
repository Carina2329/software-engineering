package com.campus.contest.module.contest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("edition_school_quota")
public class EditionSchoolQuotaDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long editionId;
    private Long schoolId;
    private Integer maxTeams;
    private Integer usedTeams;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @TableLogic
    private Integer deleted;
    @Version
    private Integer version;
}
