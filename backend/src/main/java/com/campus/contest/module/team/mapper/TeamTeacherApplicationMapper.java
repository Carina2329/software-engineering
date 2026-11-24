package com.campus.contest.module.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.contest.module.team.entity.TeamTeacherApplicationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface TeamTeacherApplicationMapper extends BaseMapper<TeamTeacherApplicationDO> {

    @Update("""
            UPDATE team_teacher_application
               SET status = 'WITHDRAWN',
                   reason = #{reason},
                   handled_at = #{now},
                   updated_at = #{now},
                   updated_by = #{operatorId},
                   version = version + 1
             WHERE registration_id = #{registrationId}
               AND status IN ('PENDING', 'ACCEPTED')
               AND deleted = 0
            """)
    int withdrawCurrent(
            @Param("registrationId") Long registrationId,
            @Param("operatorId") Long operatorId,
            @Param("now") LocalDateTime now,
            @Param("reason") String reason
    );
}
