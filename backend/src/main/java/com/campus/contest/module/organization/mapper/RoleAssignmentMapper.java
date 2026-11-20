package com.campus.contest.module.organization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.contest.module.system.entity.SysUserRoleDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface RoleAssignmentMapper extends BaseMapper<SysUserRoleDO> {

    @Update("""
            UPDATE sys_user_role
            SET scope_type = #{scopeType},
                scope_id = #{scopeId},
                status = #{status},
                valid_to = #{validTo},
                updated_at = NOW(),
                updated_by = #{updatedBy},
                version = version + 1
            WHERE id = #{assignmentId}
              AND version = #{version}
              AND deleted = 0
            """)
    int updateScopedAssignment(
            @Param("assignmentId") Long assignmentId,
            @Param("scopeType") String scopeType,
            @Param("scopeId") Long scopeId,
            @Param("status") Integer status,
            @Param("validTo") LocalDateTime validTo,
            @Param("version") Integer version,
            @Param("updatedBy") Long updatedBy
    );
}
