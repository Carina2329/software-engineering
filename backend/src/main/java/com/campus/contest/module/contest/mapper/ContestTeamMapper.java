package com.campus.contest.module.contest.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ContestTeamMapper {

    @Select("SELECT COUNT(*) FROM team WHERE edition_id = #{editionId} AND deleted = 0")
    long countByEditionId(@Param("editionId") Long editionId);
}
