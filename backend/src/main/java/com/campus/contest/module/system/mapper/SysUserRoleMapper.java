package com.campus.contest.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.contest.module.system.entity.SysUserRoleDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRoleDO> {

    /**
     * 新增或恢复用户的基础角色。范围授权使用其他 scope_type/scope_id，不受此方法影响。
     */
    @Insert("""
            INSERT INTO sys_user_role
                (user_id, role_id, scope_type, scope_id, status, valid_from, valid_to,
                 updated_at, updated_by, deleted, deleted_at, version)
            VALUES
                (#{userId}, #{roleId}, 'GLOBAL', 0, 1, NULL, NULL,
                 NOW(), #{updatedBy}, 0, NULL, 0)
            ON DUPLICATE KEY UPDATE
                status = 1,
                valid_from = NULL,
                valid_to = NULL,
                updated_at = NOW(),
                updated_by = #{updatedBy},
                deleted = 0,
                deleted_at = NULL,
                version = version + 1
            """)
    int upsertBaseRole(
            @Param("userId") Long userId,
            @Param("roleId") Long roleId,
            @Param("updatedBy") Long updatedBy
    );

    /**
     * 仅逻辑删除不再保留的基础角色，组织、省份和赛事范围授权保持原样。
     */
    @Update("""
            <script>
            UPDATE sys_user_role
            SET deleted = 1,
                deleted_at = NOW(),
                status = 0,
                updated_at = NOW(),
                updated_by = #{updatedBy},
                version = version + 1
            WHERE user_id = #{userId}
              AND scope_type = 'GLOBAL'
              AND scope_id = 0
              AND deleted = 0
              AND role_id NOT IN
              <foreach collection="retainedRoleIds" item="roleId" open="(" separator="," close=")">
                #{roleId}
              </foreach>
            </script>
            """)
    int deleteBaseRolesExcept(
            @Param("userId") Long userId,
            @Param("retainedRoleIds") List<Long> retainedRoleIds,
            @Param("updatedBy") Long updatedBy
    );
}
