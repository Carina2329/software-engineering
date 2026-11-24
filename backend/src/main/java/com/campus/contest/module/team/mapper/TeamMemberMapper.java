package com.campus.contest.module.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.contest.module.team.entity.TeamMemberDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface TeamMemberMapper extends BaseMapper<TeamMemberDO> {

    /**
     * 显式软删除成员，保证活跃唯一索引立即释放，并保留版本条件。
     */
    @Update("""
            UPDATE team_member
               SET status = 0,
                   deleted = 1,
                   deleted_at = #{now},
                   updated_at = #{now},
                   updated_by = #{operatorId},
                   version = version + 1
             WHERE id = #{memberId}
               AND deleted = 0
               AND version = #{version}
            """)
    int markInactive(
            @Param("memberId") Long memberId,
            @Param("version") Integer version,
            @Param("operatorId") Long operatorId,
            @Param("now") LocalDateTime now
    );
}
