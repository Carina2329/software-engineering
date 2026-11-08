package com.campus.contest.module.work.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文件数据范围判定（只读）。
 *
 * FileAccessGuard 在“上传人本人 / 管理员”之外，还要回答三个数据范围问题：
 * - TEAM：当前用户是不是该文件所属作品版本的<b>有效在队</b>队员；
 * - GUIDED：当前用户是不是该文件所属队伍的指导教师；
 * - ASSIGNED：当前用户是不是该文件所属作品版本的指派评委。
 *
 * 关联链路固定为 file → work_file → work_submission → team / team_member / review_assignment，
 * 不新增表，只读既有业务表。所有业务表都带逻辑删除标志，一律过滤 deleted = 0。
 *
 * TEAM 判定必须同时满足三个条件，缺一都会造成越权：
 * 1. team_member.deleted = 0：成员关系记录未被逻辑删除；
 * 2. team_member.status = 1：成员处于在队状态（status 取值与 OpenAPI 一致，1 = IN 在队，0 = LEFT 离队）；
 * 3. team.deleted = 0：队伍本身未被逻辑删除（已解散/删除的队伍不能凭旧成员记录继续访问文件）。
 */
@Mapper
public interface FileAccessScopeMapper {

    /**
     * TEAM：文件所属作品版本对应的队伍里是否有当前用户的“有效在队”记录。
     *
     * 先 join team 判队伍未删除，再要求成员记录未删除且 status = 1，
     * 这样离队成员（status = 0）和已删除队伍的旧成员都会被过滤掉。
     */
    @Select("""
            SELECT COUNT(1)
            FROM work_file wf
            JOIN work_submission ws ON ws.id = wf.submission_id AND ws.deleted = 0
            JOIN team t ON t.id = ws.team_id AND t.deleted = 0
            JOIN team_member tm ON tm.team_id = t.id AND tm.deleted = 0 AND tm.status = 1
            WHERE wf.file_id = #{fileId}
              AND tm.user_id = #{userId}
            """)
    long countTeamAccess(@Param("fileId") Long fileId, @Param("userId") Long userId);

    /** GUIDED：文件所属作品版本对应的队伍，其指导教师是否为当前用户（队伍须未删除） */
    @Select("""
            SELECT COUNT(1)
            FROM work_file wf
            JOIN work_submission ws ON ws.id = wf.submission_id AND ws.deleted = 0
            JOIN team t ON t.id = ws.team_id AND t.deleted = 0
            WHERE wf.file_id = #{fileId}
              AND t.teacher_id = #{userId}
            """)
    long countGuidedAccess(@Param("fileId") Long fileId, @Param("userId") Long userId);

    /** ASSIGNED：文件的所属作品版本上是否指派了当前用户为评委（指派记录须未删除） */
    @Select("""
            SELECT COUNT(1)
            FROM work_file wf
            JOIN review_assignment ra ON ra.submission_id = wf.submission_id AND ra.deleted = 0
            WHERE wf.file_id = #{fileId}
              AND ra.reviewer_id = #{userId}
            """)
    long countAssignedAccess(@Param("fileId") Long fileId, @Param("userId") Long userId);
}
