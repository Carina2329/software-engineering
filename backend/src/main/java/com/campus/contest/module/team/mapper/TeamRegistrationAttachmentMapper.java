package com.campus.contest.module.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.contest.module.team.entity.TeamRegistrationAttachmentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TeamRegistrationAttachmentMapper extends BaseMapper<TeamRegistrationAttachmentDO> {

    record AttachmentRow(
            Long fileId,
            String originalName,
            String fileType,
            Integer sortNo
    ) {
    }

    @Select("""
            SELECT f.id AS file_id,
                   f.original_name,
                   a.file_type,
                   a.sort_no
              FROM team_registration_attachment a
              JOIN file f
                ON f.id = a.file_id
               AND f.deleted = 0
             WHERE a.registration_id = #{registrationId}
             ORDER BY a.sort_no ASC, a.id ASC
            """)
    List<AttachmentRow> selectByRegistrationId(
            @Param("registrationId") Long registrationId
    );
}
