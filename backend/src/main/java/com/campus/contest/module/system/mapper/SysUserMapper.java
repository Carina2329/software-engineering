package com.campus.contest.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.contest.module.system.entity.SysUserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUserDO> {

    /**
     * 统计除当前用户外占用同一学号的账号数。
     *
     * 直接使用原生 SQL 而不是 LambdaQueryWrapper：sys_user 有 @TableLogic，
     * 逻辑删除的旧账号仍然占用 uk_sys_user_student_no 唯一索引，
     * 只有绕过逻辑删除过滤才能和数据库唯一约束保持一致。
     */
    @Select("SELECT COUNT(1) FROM sys_user WHERE student_no = #{studentNo} AND id <> #{excludeUserId}")
    long countByStudentNoExcluding(
            @Param("studentNo") String studentNo,
            @Param("excludeUserId") Long excludeUserId
    );
}
