package com.campus.contest.module.work.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RegistrationAttachmentUsageMapper {

    @Select("""
            SELECT (
              SELECT COUNT(1)
              FROM team_registration_attachment a
              JOIN team_registration r ON r.id = a.registration_id AND r.deleted = 0
              WHERE a.file_id = #{fileId}
                AND r.team_id <> #{teamId}
            ) + (
              SELECT COUNT(DISTINCT ws.team_id)
              FROM work_file wf
              JOIN work_submission ws ON ws.id = wf.submission_id AND ws.deleted = 0
              WHERE wf.file_id = #{fileId}
                AND ws.team_id <> #{teamId}
            )
            """)
    long countOtherTeamAssociations(
            @Param("fileId") Long fileId,
            @Param("teamId") Long teamId
    );
}
