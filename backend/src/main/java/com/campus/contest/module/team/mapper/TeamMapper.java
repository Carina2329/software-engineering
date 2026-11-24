package com.campus.contest.module.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.contest.module.team.entity.TeamDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TeamMapper extends BaseMapper<TeamDO> {

    @Select("SELECT * FROM team WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    TeamDO selectActiveByIdForUpdate(@Param("id") Long id);
}
