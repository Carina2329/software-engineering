package com.campus.contest.module.team.support.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RegistrationSupportMapper {

    record TeamRow(
            Long id,
            Long editionId,
            Long leaderId,
            Integer status,
            Long currentRegistrationId
    ) {
    }

    record EditionRow(
            Long id,
            Integer status,
            String visibility,
            Long schoolId,
            String provinceCode,
            String entryMode,
            LocalDateTime registerDeadline,
            Integer minTeamSize,
            Integer maxTeamSize,
            Integer crossSchoolTeamAllowed
    ) {
    }

    record MemberRow(
            Long id,
            Long userId,
            Long editionId,
            String memberName,
            Long schoolId,
            String studentNo,
            Integer isLeader,
            Integer status
    ) {
    }

    record GrantRow(
            Long id,
            String grantType,
            Long targetTeamId
    ) {
    }

    record QuotaRow(
            Long id,
            Integer maxTeams,
            Integer usedTeams
    ) {
    }

    record RegistrationRow(
            Long id,
            Long teamId,
            Long editionId,
            String status,
            String membersSnapshot,
            Integer quotaHeld,
            Long quotaSchoolId
    ) {
    }

    @Select("""
            SELECT id, edition_id, leader_id, status, current_registration_id
            FROM team
            WHERE id = #{teamId} AND deleted = 0
            """)
    TeamRow selectTeam(@Param("teamId") Long teamId);

    @Select("""
            SELECT id, status, visibility, school_id, province_code, entry_mode,
                   register_deadline, min_team_size, max_team_size,
                   cross_school_team_allowed
            FROM contest_edition
            WHERE id = #{editionId} AND deleted = 0
            """)
    EditionRow selectEdition(@Param("editionId") Long editionId);

    @Select("""
            SELECT id, user_id, edition_id, member_name, school_id, student_no,
                   is_leader, status
            FROM team_member
            WHERE team_id = #{teamId}
              AND status = 1
              AND deleted = 0
            ORDER BY is_leader DESC, id ASC
            """)
    List<MemberRow> selectActiveMembers(@Param("teamId") Long teamId);

    @Select("""
            SELECT id, grant_type, target_team_id
            FROM edition_entry_grant
            WHERE target_edition_id = #{editionId}
              AND status = 'ACTIVE'
              AND deleted = 0
              AND (
                (#{leaderUserId} IS NOT NULL AND leader_user_id = #{leaderUserId})
                OR (school_id = #{schoolId} AND student_no = #{studentNo})
              )
              AND (target_team_id IS NULL OR target_team_id = #{teamId})
            ORDER BY CASE WHEN target_team_id = #{teamId} THEN 0 ELSE 1 END, id DESC
            LIMIT 1
            """)
    GrantRow selectActiveGrant(
            @Param("editionId") Long editionId,
            @Param("teamId") Long teamId,
            @Param("leaderUserId") Long leaderUserId,
            @Param("schoolId") Long schoolId,
            @Param("studentNo") String studentNo
    );

    @Select("""
            SELECT id, max_teams, used_teams
            FROM edition_school_quota
            WHERE edition_id = #{editionId}
              AND school_id = #{schoolId}
              AND deleted = 0
            LIMIT 1
            """)
    QuotaRow selectQuota(
            @Param("editionId") Long editionId,
            @Param("schoolId") Long schoolId
    );

    @Select("""
            SELECT id, max_teams, used_teams
            FROM edition_school_quota
            WHERE edition_id = #{editionId}
              AND school_id = #{schoolId}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    QuotaRow selectQuotaForUpdate(
            @Param("editionId") Long editionId,
            @Param("schoolId") Long schoolId
    );

    @Select("""
            SELECT COUNT(1)
            FROM team_registration
            WHERE team_id = #{teamId}
              AND edition_id = #{editionId}
              AND deleted = 0
              AND status IN ('PENDING_TEACHER', 'PENDING_ADMIN', 'APPROVED')
            """)
    long countLiveRegistration(
            @Param("teamId") Long teamId,
            @Param("editionId") Long editionId
    );

    @Select("""
            SELECT id, team_id, edition_id, status, members_snapshot,
                   quota_held, quota_school_id
            FROM team_registration
            WHERE id = #{registrationId} AND deleted = 0
            """)
    RegistrationRow selectRegistration(@Param("registrationId") Long registrationId);

    @Select("""
            SELECT id, team_id, edition_id, status, members_snapshot,
                   quota_held, quota_school_id
            FROM team_registration
            WHERE id = #{registrationId} AND deleted = 0
            FOR UPDATE
            """)
    RegistrationRow selectRegistrationForUpdate(
            @Param("registrationId") Long registrationId
    );

    @Update("""
            UPDATE edition_school_quota
            SET used_teams = used_teams + 1,
                version = version + 1
            WHERE id = #{quotaId}
              AND (max_teams IS NULL OR used_teams < max_teams)
            """)
    int incrementQuota(@Param("quotaId") Long quotaId);

    @Update("""
            UPDATE edition_school_quota
            SET used_teams = used_teams - 1,
                version = version + 1
            WHERE id = #{quotaId}
              AND used_teams > 0
            """)
    int decrementQuota(@Param("quotaId") Long quotaId);

    @Update("""
            UPDATE team_registration
            SET quota_snapshot = CAST(#{snapshotJson} AS JSON),
                quota_held = #{held},
                quota_school_id = #{schoolId},
                version = version + 1
            WHERE id = #{registrationId}
              AND deleted = 0
            """)
    int updateQuotaSnapshot(
            @Param("registrationId") Long registrationId,
            @Param("schoolId") Long schoolId,
            @Param("snapshotJson") String snapshotJson,
            @Param("held") int held
    );

    @Update("""
            UPDATE team_registration
            SET quota_held = 0,
                version = version + 1
            WHERE id = #{registrationId}
              AND deleted = 0
              AND quota_held = 1
            """)
    int clearQuotaHeld(@Param("registrationId") Long registrationId);
}
