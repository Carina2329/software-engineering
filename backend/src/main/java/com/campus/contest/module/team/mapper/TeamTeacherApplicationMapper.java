package com.campus.contest.module.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.contest.module.team.entity.TeamTeacherApplicationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface TeamTeacherApplicationMapper extends BaseMapper<TeamTeacherApplicationDO> {

    @Select("""
            SELECT COUNT(*)
              FROM team_teacher_application app
              JOIN team_registration reg ON reg.id = app.registration_id
              JOIN team t ON t.id = reg.team_id
             WHERE app.teacher_id = #{teacherId}
               AND app.status IN ('PENDING', 'ACCEPTED')
               AND app.deleted = 0
               AND reg.deleted = 0
               AND reg.status IN ('PENDING_TEACHER', 'PENDING_ADMIN', 'APPROVED')
               AND reg.edition_id = #{editionId}
               AND t.deleted = 0
               AND t.current_registration_id = reg.id
            """)
    long countEffectiveForEdition(
            @Param("teacherId") Long teacherId,
            @Param("editionId") Long editionId
    );
    @Select("""
            SELECT COUNT(*)
              FROM team_teacher_application app
              JOIN team_registration reg ON reg.id = app.registration_id
              JOIN team t ON t.id = reg.team_id
             WHERE app.registration_id = #{registrationId}
               AND app.teacher_id = #{teacherId}
               AND app.status IN ('PENDING', 'ACCEPTED')
               AND app.deleted = 0
               AND reg.deleted = 0
               AND reg.status IN ('PENDING_TEACHER', 'PENDING_ADMIN', 'APPROVED')
               AND t.deleted = 0
               AND t.current_registration_id = reg.id
            """)
    long countEffectiveForRegistration(
            @Param("registrationId") Long registrationId,
            @Param("teacherId") Long teacherId
    );
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
