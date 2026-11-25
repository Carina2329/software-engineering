package com.campus.contest.module.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.contest.module.team.entity.TeamDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface TeamMapper extends BaseMapper<TeamDO> {

    @Select("SELECT * FROM team WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    TeamDO selectActiveByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE team
               SET teacher_id = #{teacherId},
                   status = #{status},
                   updated_at = #{now},
                   updated_by = #{operatorId},
                   version = version + 1
             WHERE id = #{teamId}
               AND version = #{version}
               AND deleted = 0
            """)
    int updateTeacherAndStatus(
            @Param("teamId") Long teamId,
            @Param("version") Integer version,
            @Param("teacherId") Long teacherId,
            @Param("operatorId") Long operatorId,
            @Param("now") LocalDateTime now,
            @Param("status") Integer status
    );

    @Update("""
            UPDATE team
               SET status = #{status},
                   updated_at = #{now},
                   updated_by = #{operatorId},
                   version = version + 1
             WHERE id = #{teamId}
               AND version = #{version}
               AND deleted = 0
            """)
    int updateStatus(
            @Param("teamId") Long teamId,
            @Param("version") Integer version,
            @Param("operatorId") Long operatorId,
            @Param("now") LocalDateTime now,
            @Param("status") Integer status
    );
    /**
     * 撤回报名时显式清空 teacher_id，避免 updateById 忽略 null。
     */
    @Update("""
            UPDATE team
               SET status = #{status},
                   teacher_id = NULL,
                   updated_at = #{now},
                   updated_by = #{operatorId},
                   version = version + 1
             WHERE id = #{teamId}
               AND version = #{version}
               AND deleted = 0
            """)
    int withdrawAndClearTeacher(
            @Param("teamId") Long teamId,
            @Param("version") Integer version,
            @Param("operatorId") Long operatorId,
            @Param("now") LocalDateTime now,
            @Param("status") Integer status
    );
}
