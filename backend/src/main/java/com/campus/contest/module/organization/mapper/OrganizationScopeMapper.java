package com.campus.contest.module.organization.mapper;

import com.campus.contest.module.organization.model.ScopeGrantDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrganizationScopeMapper {

    @Select("""
            SELECT ur.id AS assignment_id,
                   ur.role_id,
                   r.role_code,
                   r.data_scope,
                   ur.scope_type,
                   ur.scope_id,
                   ur.status,
                   ur.valid_from,
                   ur.valid_to,
                   ur.deleted
            FROM sys_user_role ur
            JOIN sys_role r
              ON r.id = ur.role_id
             AND r.deleted = 0
            WHERE ur.user_id = #{userId}
              AND ur.status = 1
              AND ur.deleted = 0
              AND (ur.valid_from IS NULL OR ur.valid_from <= #{now})
              AND (ur.valid_to IS NULL OR ur.valid_to > #{now})
            ORDER BY ur.id
            """)
    List<ScopeGrantDO> selectActiveGrants(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now
    );
}
