package com.campus.contest.module.team.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AvailableTeacherMapper {

    record AvailableTeacherRow(
            Long id,
            String realName,
            Long schoolId,
            String schoolName,
            String college,
            String title
    ) {
    }

    @Select("""
            <script>
            SELECT DISTINCT u.id,
                   u.real_name,
                   u.school_id,
                   o.org_name AS school_name,
                   u.college,
                   u.title
              FROM sys_user u
              JOIN organization o
                ON o.id = u.school_id
               AND o.deleted = 0
               AND o.status = 1
               AND o.org_type = 'SCHOOL'
              JOIN sys_user_role ur
                ON ur.user_id = u.id
               AND ur.deleted = 0
               AND ur.status = 1
               AND (ur.valid_from IS NULL OR ur.valid_from &lt;= NOW())
               AND (ur.valid_to IS NULL OR ur.valid_to &gt;= NOW())
              JOIN sys_role r
                ON r.id = ur.role_id
               AND r.deleted = 0
               AND r.role_code = 'TEACHER'
             WHERE u.deleted = 0
               AND u.status = 1
               AND u.school_verification_status = 'VERIFIED'
            <if test="keyword != null and keyword != ''">
               AND (u.real_name LIKE CONCAT('%', #{keyword}, '%')
                    OR u.username LIKE CONCAT('%', #{keyword}, '%'))
            </if>
             ORDER BY u.real_name ASC, u.id ASC
             LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<AvailableTeacherRow> selectPage(
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Select("""
            <script>
            SELECT COUNT(DISTINCT u.id)
              FROM sys_user u
              JOIN organization o
                ON o.id = u.school_id
               AND o.deleted = 0
               AND o.status = 1
               AND o.org_type = 'SCHOOL'
              JOIN sys_user_role ur
                ON ur.user_id = u.id
               AND ur.deleted = 0
               AND ur.status = 1
               AND (ur.valid_from IS NULL OR ur.valid_from &lt;= NOW())
               AND (ur.valid_to IS NULL OR ur.valid_to &gt;= NOW())
              JOIN sys_role r
                ON r.id = ur.role_id
               AND r.deleted = 0
               AND r.role_code = 'TEACHER'
             WHERE u.deleted = 0
               AND u.status = 1
               AND u.school_verification_status = 'VERIFIED'
            <if test="keyword != null and keyword != ''">
               AND (u.real_name LIKE CONCAT('%', #{keyword}, '%')
                    OR u.username LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            </script>
            """)
    long count(@Param("keyword") String keyword);
}
