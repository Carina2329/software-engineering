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

    @Select("SELECT * FROM team_registration WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    TeamRegistrationDO selectActiveByIdForUpdate(@Param("id") Long id);
}
