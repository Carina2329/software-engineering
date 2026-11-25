package com.campus.contest.module.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.team.entity.EditionEntryGrantDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EditionEntryGrantMapper extends BaseMapper<EditionEntryGrantDO> {

    @Select("SELECT * FROM contest_edition WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    ContestEditionDO selectEditionForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM edition_entry_grant WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    EditionEntryGrantDO selectActiveByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT *
              FROM edition_entry_grant
             WHERE target_edition_id = #{editionId}
               AND school_id = #{schoolId}
               AND student_no = #{studentNo}
               AND status = 'ACTIVE'
               AND deleted = 0
             LIMIT 1
             FOR UPDATE
            """)
    EditionEntryGrantDO selectActiveByKeyForUpdate(
            @Param("editionId") Long editionId,
            @Param("schoolId") Long schoolId,
            @Param("studentNo") String studentNo
    );

    @Update("""
            UPDATE edition_entry_grant
               SET grant_type = #{record.grantType},
                   leader_user_id = #{record.leaderUserId},
                   leader_name = #{record.leaderName},
                   source_edition_id = #{record.sourceEditionId},
                   source_team_code = #{record.sourceTeamCode},
                   reason = #{record.reason},
                   updated_at = #{record.updatedAt},
                   updated_by = #{record.updatedBy},
                   version = version + 1
             WHERE id = #{record.id}
               AND status = 'ACTIVE'
               AND version = #{record.version}
               AND deleted = 0
            """)
    int updateActiveRecord(@Param("record") EditionEntryGrantDO record);

    @Update("""
            UPDATE edition_entry_grant
               SET status = 'REVOKED',
                   reason = #{record.reason},
                   updated_at = #{record.updatedAt},
                   updated_by = #{record.updatedBy},
                   revoked_at = #{record.revokedAt},
                   revoked_by = #{record.revokedBy},
                   version = version + 1
             WHERE id = #{record.id}
               AND target_edition_id = #{record.targetEditionId}
               AND status = 'ACTIVE'
               AND version = #{record.version}
               AND deleted = 0
            """)
    int revokeActiveRecord(@Param("record") EditionEntryGrantDO record);
}
