package com.campus.contest.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.contest.module.system.entity.SysUserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUserDO> {

    /**
     * 统计全平台范围内除当前用户外占用同一学号的账号数。
     *
     * 多组织基线已把学号唯一约束改为 uk_sys_user_school_student(school_id, student_no)，
     * 本方法只在“尚未选校（school_id 为空）”时作为保守兜底使用；
     * 已选校学生走 {@link #countByStudentNoInSchoolExcluding} 的同校口径。
     *
     * 直接使用原生 SQL 而不是 LambdaQueryWrapper：sys_user 有 @TableLogic，
     * 逻辑删除的旧账号仍然占用唯一索引，只有绕过逻辑删除过滤才能和数据库约束保持一致。
     */
    @Select("SELECT COUNT(1) FROM sys_user WHERE student_no = #{studentNo} AND id <> #{excludeUserId}")
    long countByStudentNoExcluding(
            @Param("studentNo") String studentNo,
            @Param("excludeUserId") Long excludeUserId
    );

    /**
     * 统计同一学校内除当前用户外占用同一学号的账号数。
     *
     * 对应多组织基线的 uk_sys_user_school_student(school_id, student_no)：
     * 学号只在同一学校内唯一，不同学校允许相同学号。
     * 同样使用原生 SQL 绕过 @TableLogic，与数据库唯一约束保持一致。
     */
    @Select("""
            SELECT COUNT(1) FROM sys_user
            WHERE student_no = #{studentNo}
              AND school_id = #{schoolId}
              AND id <> #{excludeUserId}
            """)
    long countByStudentNoInSchoolExcluding(
            @Param("studentNo") String studentNo,
            @Param("schoolId") Long schoolId,
            @Param("excludeUserId") Long excludeUserId
    );
}
