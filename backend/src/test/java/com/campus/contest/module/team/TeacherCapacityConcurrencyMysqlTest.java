package com.campus.contest.module.team;

import com.campus.contest.config.MybatisPlusConfig;
import com.campus.contest.module.team.entity.TeamDO;
import com.campus.contest.module.team.entity.TeamRegistrationDO;
import com.campus.contest.module.team.mapper.TeamMapper;
import com.campus.contest.module.team.mapper.TeamRegistrationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_TEAM_REGISTRATION_MYSQL_TESTS", matches = "true")
@SpringBootTest(
        classes = TeacherCapacityConcurrencyMysqlTest.TestApplication.class,
        properties = {
                "spring.main.web-application-type=none",
                "spring.datasource.url=${TEAM_REGISTRATION_TEST_DB_URL:jdbc:mysql://localhost:3306/contest_db_dashboard_e2e?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false}",
                "spring.datasource.username=${TEAM_REGISTRATION_TEST_DB_USERNAME:root}",
                "spring.datasource.password=${TEAM_REGISTRATION_TEST_DB_PASSWORD:root123}",
                "mybatis-plus.configuration.local-cache-scope=STATEMENT"
        }
)
class TeacherCapacityConcurrencyMysqlTest {

    private static final Long EDITION_ID = 1L;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TeamMapper teamMapper;
    @Autowired
    private TeamRegistrationMapper registrationMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void databaseLockAllowsOnlyOneApprovalWhenCapacityIsOne() throws Exception {
        Integer originalMax = jdbcTemplate.queryForObject(
                "SELECT max_teams_per_teacher FROM contest_edition WHERE id = ?",
                Integer.class,
                EDITION_ID
        );
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Long teacherId = insertTeacherUser(suffix);
        Long firstTeamId = null;
        Long secondTeamId = null;
        Long firstRegistrationId = null;
        Long secondRegistrationId = null;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            jdbcTemplate.update(
                    "UPDATE contest_edition SET max_teams_per_teacher = 1 WHERE id = ?",
                    EDITION_ID
            );
            firstTeamId = insertTeam("CAP" + suffix + "A", suffix + "A");
            secondTeamId = insertTeam("CAP" + suffix + "B", suffix + "B");
            firstRegistrationId = insertRegistration(firstTeamId);
            secondRegistrationId = insertRegistration(secondTeamId);
            insertPendingApplication(firstRegistrationId, teacherId);
            insertPendingApplication(secondRegistrationId, teacherId);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_user WHERE id = ? AND status = 1 AND deleted = 0",
                    Integer.class,
                    teacherId
            )).isEqualTo(1);

            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            final Long firstTeam = firstTeamId;
            final Long secondTeam = secondTeamId;
            final Long firstRegistration = firstRegistrationId;
            final Long secondRegistration = secondRegistrationId;
            Future<Boolean> first = executor.submit(() ->
                    approveWhenCapacityAvailable(transaction, firstTeam, firstRegistration, teacherId, ready, start)
            );
            Future<Boolean> second = executor.submit(() ->
                    approveWhenCapacityAvailable(transaction, secondTeam, secondRegistration, teacherId, ready, start)
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Boolean> outcomes = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );
            assertThat(outcomes).containsExactlyInAnyOrder(true, false);
            Integer accepted = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                      FROM team_teacher_application
                     WHERE registration_id IN (?, ?)
                       AND status = 'ACCEPTED'
                    """,
                    Integer.class,
                    firstRegistrationId,
                    secondRegistrationId
            );
            assertThat(accepted).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            if (firstRegistrationId != null && secondRegistrationId != null) {
                jdbcTemplate.update(
                        "DELETE FROM team_teacher_application WHERE registration_id IN (?, ?)",
                        firstRegistrationId,
                        secondRegistrationId
                );
                jdbcTemplate.update(
                        "DELETE FROM team_registration WHERE id IN (?, ?)",
                        firstRegistrationId,
                        secondRegistrationId
                );
            }
            if (firstTeamId != null && secondTeamId != null) {
                jdbcTemplate.update(
                        "DELETE FROM team WHERE id IN (?, ?)",
                        firstTeamId,
                        secondTeamId
                );
            }
            jdbcTemplate.update(
                    "UPDATE contest_edition SET max_teams_per_teacher = ? WHERE id = ?",
                    originalMax,
                    EDITION_ID
            );
            jdbcTemplate.update(
                    "DELETE FROM sys_user WHERE id = ?",
                    teacherId
            );
        }
    }

    private boolean approveWhenCapacityAvailable(
            TransactionTemplate transaction,
            Long teamId,
            Long registrationId,
            Long teacherId,
                    CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await(10, TimeUnit.SECONDS);
        Boolean approved = transaction.execute(status -> {
            TeamDO team = teamMapper.selectActiveByIdForUpdate(teamId);
            if (team == null) {
                throw new IllegalStateException("team not found: " + teamId);
            }
            TeamRegistrationDO registration =
                    registrationMapper.selectActiveByIdForUpdate(registrationId);
            if (registration == null) {
                throw new IllegalStateException("registration not found: " + registrationId);
            }
            if (teamMapper.lockActiveUserForUpdate(teacherId) == null) {
                throw new IllegalStateException("teacher not active: " + teacherId);
            }
            long activeLoad = registrationMapper.countActiveTeacherLoad(
                    EDITION_ID,
                    teacherId,
                    registrationId
            );
            if (activeLoad >= 1) {
                return false;
            }
            int applicationUpdated = jdbcTemplate.update(
                    """
                    UPDATE team_teacher_application
                       SET status = 'ACCEPTED', handled_at = ?, version = version + 1
                     WHERE registration_id = ? AND status = 'PENDING' AND deleted = 0
                    """,
                    LocalDateTime.now(),
                    registrationId
            );
            int registrationUpdated = jdbcTemplate.update(
                    """
                    UPDATE team_registration
                       SET status = 'PENDING_ADMIN', version = version + 1
                     WHERE id = ? AND status = 'PENDING_TEACHER' AND deleted = 0
                    """,
                    registrationId
            );
            int teamUpdated = jdbcTemplate.update(
                    """
                    UPDATE team
                       SET teacher_id = ?, status = 2, version = version + 1
                     WHERE id = ? AND version = ? AND deleted = 0
                    """,
                    teacherId,
                    teamId,
                    team.getVersion()
            );
            if (applicationUpdated != 1 || registrationUpdated != 1 || teamUpdated != 1) {
                throw new IllegalStateException(
                        "concurrent approval update failed: application=" + applicationUpdated
                                + ", registration=" + registrationUpdated
                                + ", team=" + teamUpdated
                );
            }
            return true;
        });
        return Boolean.TRUE.equals(approved);
    }

    private Long insertTeacherUser(String suffix) {
        String username = "cap-user-" + suffix;
        jdbcTemplate.update(
                """
                INSERT INTO sys_user
                    (username, real_name, password_hash, email, student_no, school_id,
                     school_verification_status, status, created_at, created_by, updated_at, updated_by, deleted, version)
                VALUES (?, 'Capacity Teacher', 'x', ?, ?, 2, 'VERIFIED', 1, NOW(), 1, NOW(), 1, 0, 0)
                """,
                username,
                username + "@example.test",
                "T" + suffix
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?",
                Long.class,
                username
        );
    }

    private Long insertTeam(String teamCode, String studentSuffix) {
        jdbcTemplate.update(
                """
                INSERT INTO team
                    (edition_id, team_name, team_code, leader_id, status, member_count,
                     created_by, deleted, version)
                VALUES (?, ?, ?, 2, 1, 1, 2, 0, 0)
                """,
                EDITION_ID,
                "Capacity " + studentSuffix,
                teamCode
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM team WHERE team_code = ?",
                Long.class,
                teamCode
        );
    }

    private Long insertRegistration(Long teamId) {
        jdbcTemplate.update(
                """
                INSERT INTO team_registration
                    (team_id, edition_id, round_no, status, members_snapshot,
                     quota_held, submitted_at, created_by, deleted, version)
                VALUES (?, ?, 1, 'PENDING_TEACHER', CAST('[{"member_id":"1"}]' AS JSON), 0, NOW(), 2, 0, 0)
                """,
                teamId,
                EDITION_ID
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM team_registration WHERE team_id = ?",
                Long.class,
                teamId
        );
    }

    private void insertPendingApplication(Long registrationId, Long teacherId) {
        jdbcTemplate.update(
                """
                INSERT INTO team_teacher_application
                    (registration_id, teacher_id, status, applied_at,
                     created_by, deleted, version)
                VALUES (?, ?, 'PENDING', NOW(), 2, 0, 0)
                """,
                registrationId,
                teacherId
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @MapperScan("com.campus.contest.module.team.mapper")
    @Import(MybatisPlusConfig.class)
    static class TestApplication {
    }
}
