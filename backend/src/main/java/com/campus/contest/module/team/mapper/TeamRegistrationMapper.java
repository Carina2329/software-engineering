package com.campus.contest.module.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.contest.module.team.entity.TeamRegistrationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TeamRegistrationMapper extends BaseMapper<TeamRegistrationDO> {

    @Select("""
            SELECT COALESCE(MAX(round_no), 0)
              FROM team_registration
             WHERE team_id = #{teamId}
            """)
    int selectMaxRoundNo(@Param("teamId") Long teamId);

    @Select("""
            SELECT COUNT(DISTINCT reg.id)
              FROM team_registration reg
              JOIN team_teacher_application app ON app.registration_id = reg.id
              JOIN team t ON t.id = reg.team_id
             WHERE reg.edition_id = #{editionId}
               AND app.teacher_id = #{teacherId}
               AND app.status = 'ACCEPTED'
               AND app.deleted = 0
               AND reg.deleted = 0
               AND reg.status IN ('PENDING_ADMIN', 'APPROVED')
               AND reg.id <> #{excludeRegistrationId}
               AND t.deleted = 0
            """)
    long countActiveTeacherLoad(
            @Param("editionId") Long editionId,
            @Param("teacherId") Long teacherId,
            @Param("excludeRegistrationId") Long excludeRegistrationId
    );
    @Select("SELECT * FROM team_registration WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    TeamRegistrationDO selectActiveByIdForUpdate(@Param("id") Long id);
}
