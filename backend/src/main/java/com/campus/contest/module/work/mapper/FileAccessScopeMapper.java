package com.campus.contest.module.work.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文件数据范围判定（只读）。
 *
 * FileAccessGuard 在“上传人本人 / 管理员”之外，还要回答三个数据范围问题：
 * - TEAM：当前用户是不是该文件所属作品版本的队员；
 * - GUIDED：当前用户是不是该文件所属队伍的指导教师；
 * - ASSIGNED：当前用户是不是该文件所属作品版本的指派评委。
 *
 * 关联链路固定为 file → work_file → work_submission → team / team_member / review_assignment，
 * 不新增表，只读既有业务表；三张业务表都带逻辑删除标志，这里全部过滤 deleted = 0。
 */
@Mapper
public interface FileAccessScopeMapper {

    /** TEAM：文件所属作品版本对应的队伍里是否有当前用户 */
    @Select("""
            SELECT COUNT(1)
            FROM work_file wf
            JOIN work_submission ws ON ws.id = wf.submission_id AND ws.deleted = 0
            JOIN team_member tm ON tm.team_id = ws.team_id AND tm.deleted = 0
            WHERE wf.file_id = #{fileId}
              AND tm.user_id = #{userId}
            """)
    long countTeamAccess(@Param("fileId") Long fileId, @Param("userId") Long userId);

    /** GUIDED：文件所属作品版本对应的队伍，其指导教师是否为当前用户 */
    @Select("""
            SELECT COUNT(1)
            FROM work_file wf
            JOIN work_submission ws ON ws.id = wf.submission_id AND ws.deleted = 0
            JOIN team t ON t.id = ws.team_id AND t.deleted = 0
            WHERE wf.file_id = #{fileId}
              AND t.teacher_id = #{userId}
            """)
    long countGuidedAccess(@Param("fileId") Long fileId, @Param("userId") Long userId);

    /** ASSIGNED：文件的所属作品版本上是否指派了当前用户为评委 */
    @Select("""
            SELECT COUNT(1)
            FROM work_file wf
            JOIN review_assignment ra ON ra.submission_id = wf.submission_id AND ra.deleted = 0
            WHERE wf.file_id = #{fileId}
              AND ra.reviewer_id = #{userId}
            """)
    long countAssignedAccess(@Param("fileId") Long fileId, @Param("userId") Long userId);
}
