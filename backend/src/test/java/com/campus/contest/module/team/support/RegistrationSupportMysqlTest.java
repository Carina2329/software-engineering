package com.campus.contest.module.team.support;

import com.campus.contest.config.MybatisPlusConfig;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper;
import com.campus.contest.module.team.mapper.TeamMapper;
import com.campus.contest.module.team.support.service.impl.SchoolQuotaServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_TEAM_REGISTRATION_MYSQL_TESTS", matches = "true")
@SpringBootTest(
        classes = RegistrationSupportMysqlTest.TestApplication.class,
        properties = {
                "spring.main.web-application-type=none",
                "spring.datasource.url=${TEAM_REGISTRATION_TEST_DB_URL:jdbc:mysql://localhost:3306/contest_db_dashboard_e2e?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false}",
                "spring.datasource.username=${TEAM_REGISTRATION_TEST_DB_USERNAME:root}",
                "spring.datasource.password=${TEAM_REGISTRATION_TEST_DB_PASSWORD:root123}",
                "mybatis-plus.configuration.local-cache-scope=STATEMENT"
        }
)
@Transactional
class RegistrationSupportMysqlTest {

    @Autowired
    private RegistrationSupportMapper mapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TeamMapper teamMapper;
    @Autowired
    private com.campus.contest.module.team.mapper.TeamTeacherApplicationMapper teacherApplicationMapper;

    @Test
    void mapperAndQuotaServiceWorkAgainstRealMysql() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        jdbcTemplate.update(
                """
                INSERT INTO team
                    (edition_id, team_name, team_code, leader_id, teacher_id,
                     status, member_count, created_by, deleted, version)
                VALUES (1, ?, ?, 2, 8, 1, 1, 2, 0, 0)
                """,
                "Support Test " + suffix,
                "ST" + suffix
        );
        Long teamId = jdbcTemplate.queryForObject(
                "SELECT id FROM team WHERE team_code = ?",
                Long.class,
                "ST" + suffix
        );
        jdbcTemplate.update(
                """
                INSERT INTO team_member
                    (team_id, edition_id, user_id, member_name, school_id, student_no,
                     is_leader, status, created_by, deleted, version)
                VALUES (?, 1, 2, 'Student A', 2, 'SUPPORT-A', 1, 1, 2, 0, 0)
                """,
                teamId
        );

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        String snapshot = objectMapper.writeValueAsString(objectMapper.createArrayNode().add(
                objectMapper.createObjectNode()
                        .put("member_id", 1)
                        .put("user_id", 2)
                        .put("member_name", "Student A")
                        .put("school_id", 2)
                        .put("student_no", "SUPPORT-A")
                        .put("is_leader", true)
        ));
        jdbcTemplate.update(
                """
                INSERT INTO team_registration
                    (team_id, edition_id, round_no, status, members_snapshot,
                     quota_held, submitted_at, created_by, deleted, version)
                VALUES (?, 1, 1, 'PENDING_TEACHER', CAST(? AS JSON), 0, NOW(), 2, 0, 0)
                """,
                teamId,
                snapshot
        );
        Long registrationId = jdbcTemplate.queryForObject(
                "SELECT id FROM team_registration WHERE team_id = ?",
                Long.class,
                teamId
        );
        jdbcTemplate.update(
                "UPDATE team SET current_registration_id = ? WHERE id = ?",
                registrationId,
                teamId
        );
        jdbcTemplate.update(
                """
                INSERT INTO team_teacher_application
                    (registration_id, teacher_id, status, applied_at,
                     created_by, deleted, version)
                VALUES (?, 8, 'ACCEPTED', NOW(), 2, 0, 0)
                """,
                registrationId
        );
        assertThat(teacherApplicationMapper.countEffectiveForRegistration(registrationId, 8L))
                .isEqualTo(1L);
        jdbcTemplate.update("UPDATE team_registration SET status = 'REJECTED' WHERE id = ?", registrationId);
        assertThat(teacherApplicationMapper.countEffectiveForRegistration(registrationId, 8L))
                .isZero();
        jdbcTemplate.update("UPDATE team_registration SET status = 'PENDING_TEACHER' WHERE id = ?", registrationId);
        jdbcTemplate.update(
                "UPDATE team SET current_registration_id = ? WHERE id = ?",
                registrationId,
                teamId
        );
        jdbcTemplate.update(
                """
                INSERT INTO edition_entry_grant
                    (target_edition_id, grant_type, leader_user_id, leader_name,
                     school_id, student_no, target_team_id, status, created_by, deleted, version)
                VALUES (1, 'QUALIFIED', 2, 'Student A', 2, 'SUPPORT-A', ?, 'ACTIVE', 1, 0, 0)
                """,
                teamId
        );
        jdbcTemplate.update(
                """
                INSERT INTO edition_school_quota
                    (edition_id, school_id, max_teams, used_teams, created_by, deleted, version)
                VALUES (1, 2, 2, 0, 1, 0, 0)
                ON DUPLICATE KEY UPDATE max_teams = 2, used_teams = 0, deleted = 0
                """
        );

        assertThat(mapper.selectTeam(teamId).leaderId()).isEqualTo(2L);
        assertThat(mapper.selectEdition(1L).entryMode()).isNotBlank();
        assertThat(mapper.selectActiveMembers(teamId)).hasSize(1);
        assertThat(mapper.selectActiveGrant(1L, teamId, 2L, 2L, "SUPPORT-A")).isNotNull();

        SchoolQuotaServiceImpl quotaService = new SchoolQuotaServiceImpl(
                mapper,
                objectMapper,
                Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneId.of("Asia/Shanghai"))
        );
        assertThat(quotaService.occupy(registrationId, 1L, 2L).held()).isTrue();
        assertThat(quotaService.occupy(registrationId, 1L, 2L).held()).isTrue();
        quotaService.release(registrationId);
        quotaService.release(registrationId);

        Integer used = jdbcTemplate.queryForObject(
                "SELECT used_teams FROM edition_school_quota WHERE edition_id = 1 AND school_id = 2",
                Integer.class
        );
        assertThat(used).isZero();

        Long teacherIdBeforeWithdraw = jdbcTemplate.queryForObject(
                "SELECT teacher_id FROM team WHERE id = ?",
                Long.class,
                teamId
        );
        assertThat(teacherIdBeforeWithdraw).isEqualTo(8L);

        assertThat(teamMapper.withdrawAndClearTeacher(
                teamId,
                0,
                2L,
                LocalDateTime.of(2026, 11, 24, 18, 0),
                5
        )).isEqualTo(1);
        String teacherId = jdbcTemplate.queryForObject(
                "SELECT teacher_id FROM team WHERE id = ?",
                String.class,
                teamId
        );
        assertThat(teacherId).isNull();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @MapperScan({"com.campus.contest.module.team.support.mapper", "com.campus.contest.module.team.mapper"})
    @Import(MybatisPlusConfig.class)
    static class TestApplication {
    }
}
