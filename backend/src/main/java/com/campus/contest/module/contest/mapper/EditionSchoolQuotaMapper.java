package com.campus.contest.module.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.contest.module.contest.entity.EditionSchoolQuotaDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface EditionSchoolQuotaMapper extends BaseMapper<EditionSchoolQuotaDO> {

    @Select("""
            SELECT *
              FROM edition_school_quota
             WHERE edition_id = #{editionId}
             ORDER BY school_id
             FOR UPDATE
            """)
    List<EditionSchoolQuotaDO> selectByEditionForUpdate(
            @Param("editionId") Long editionId
    );

    @Update("""
            UPDATE edition_school_quota
               SET max_teams = #{maxTeams},
                   deleted = 0,
                   updated_at = #{now},
                   updated_by = #{operatorId},
                   version = version + 1
             WHERE id = #{id}
            """)
    int updateMaxTeams(
            @Param("id") Long id,
            @Param("maxTeams") Integer maxTeams,
            @Param("operatorId") Long operatorId,
            @Param("now") LocalDateTime now
    );

    @Update("""
            UPDATE edition_school_quota
               SET deleted = 1,
                   updated_at = #{now},
                   updated_by = #{operatorId},
                   version = version + 1
             WHERE id = #{id}
               AND deleted = 0
               AND used_teams = 0
            """)
    int softDeleteIfUnused(
            @Param("id") Long id,
            @Param("operatorId") Long operatorId,
            @Param("now") LocalDateTime now
    );
}
