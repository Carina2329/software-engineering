package com.campus.contest.module.dashboard;

import com.campus.contest.config.MybatisPlusConfig;
import com.campus.contest.module.dashboard.dto.DashboardDeadlineRow;
import com.campus.contest.module.dashboard.dto.DashboardLevelCountRow;
import com.campus.contest.module.dashboard.mapper.DashboardMapper;
import com.campus.contest.module.dashboard.vo.DashboardProvinceSchoolVO;
import com.campus.contest.module.dashboard.vo.DashboardUserRoleVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_DASHBOARD_MYSQL_TESTS", matches = "true")
@SpringBootTest(
        classes = DashboardMysqlTest.TestApplication.class,
        properties = "spring.main.web-application-type=none"
)
@Transactional
class DashboardMysqlTest {

    private static final LocalDateTime REFERENCE_TIME = LocalDateTime.of(2076, 11, 21, 17, 30);
    private static final String TEST_PASSWORD_HASH =
            "$2b$12$25dgA4gYN2jCJ8iYZcSMQ.PYSAgMZGOZVD7rz9Z6nRtRcJQw0FSzO";

    @Autowired
    private DashboardMapper dashboardMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    @Test
    void effectiveSchoolsRequireEnabledSchoolAndEnabledProvinceDictionary() {
        long before = dashboardMapper.countEffectiveSchools();
        long dictTypeId = requiredProvinceDictionary();
        String suffix = suffix();
        jdbcTemplate.update(
                """
                INSERT INTO sys_dict_item
                    (dict_type_id, item_label, item_value, sort_no, status, created_by, deleted, version)
                VALUES (?, '大屏测试省', '990000', 999, 1, 0, 0, 0)
                """,
                dictTypeId
        );
        insertSchool("DASH-A-" + suffix, "启用学校A", "990000", 1, 0);
        insertSchool("DASH-B-" + suffix, "启用学校B", "990000", 1, 0);
        insertSchool("DASH-C-" + suffix, "停用学校", "990000", 0, 0);
        insertSchool("DASH-D-" + suffix, "删除学校", "990000", 1, 1);
        insertSchool("DASH-E-" + suffix, "无效省份学校", "990001", 1, 0);
        sqlSessionTemplate.clearCache();

        assertThat(dashboardMapper.countEffectiveSchools()).isEqualTo(before + 2);
        List<DashboardProvinceSchoolVO> rows = dashboardMapper.selectProvinceSchools().stream()
                .filter(row -> row.provinceCode().equals("990000"))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).provinceName()).isEqualTo("大屏测试省");
        assertThat(rows.get(0).schoolCount()).isEqualTo(2);
    }

    @Test
    void activeUsersPendingAffiliationsAndRoleUsersFollowValidityAndDeduplicate() {
        long activeBefore = dashboardMapper.countActiveUsers();
        long pendingBefore = dashboardMapper.countPendingAffiliations();
        String suffix = suffix();
        insertUser("du_active_" + suffix, 1, 0);
        insertUser("du_disabled_" + suffix, 0, 0);
        insertUser("du_deleted_" + suffix, 1, 1);
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?",
                Long.class,
                "du_active_" + suffix
        );
        jdbcTemplate.update(
                """
                INSERT INTO sys_role (role_code, role_name, data_scope, created_by, deleted, version)
                VALUES (?, '大屏测试角色', 'ALL', 0, 0, 0)
                """,
                "DASH_" + suffix
        );
        long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE role_code = ?",
                Long.class,
                "DASH_" + suffix
        );
        insertRoleGrant(userId, roleId, "GLOBAL", 0L, 1, REFERENCE_TIME.minusDays(1), REFERENCE_TIME.plusDays(1));
        insertRoleGrant(userId, roleId, "ORGANIZATION", 1L, 1, REFERENCE_TIME.minusDays(1), REFERENCE_TIME.plusDays(1));
        insertRoleGrant(userId, roleId, "EVENT", 2L, 0, REFERENCE_TIME.minusDays(1), REFERENCE_TIME.plusDays(1));
        insertRoleGrant(userId, roleId, "PROVINCE", 510000L, 1, REFERENCE_TIME.minusDays(2), REFERENCE_TIME.minusDays(1));
        sqlSessionTemplate.clearCache();

        assertThat(dashboardMapper.countActiveUsers()).isEqualTo(activeBefore + 1);
        assertThat(dashboardMapper.countPendingAffiliations()).isEqualTo(pendingBefore + 1);
        List<DashboardUserRoleVO> roles = dashboardMapper.selectUserRoles(REFERENCE_TIME).stream()
                .filter(row -> row.roleCode().equals("DASH_" + suffix))
                .toList();
        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).userCount()).isEqualTo(1);
    }

    @Test
    void contestMetricsUseRealStatusDeadlineAndLevelRules() {
        long seriesBefore = dashboardMapper.countContestSeries();
        long publishedBefore = dashboardMapper.countPublishedEditions();
        long registeringBefore = dashboardMapper.countRegisteringEditions(REFERENCE_TIME);
        Map<String, Long> levelsBefore = dashboardMapper.selectLevelDistribution().stream()
                .collect(Collectors.toMap(DashboardLevelCountRow::level, DashboardLevelCountRow::count));
        String suffix = suffix();
        jdbcTemplate.update(
                """
                INSERT INTO contest (contest_name, series_code, level, created_by, deleted, version)
                VALUES ('大屏测试竞赛', ?, 'SCHOOL', 0, 0, 0)
                """,
                "DASH-SERIES-" + suffix
        );
        long contestId = jdbcTemplate.queryForObject(
                "SELECT id FROM contest WHERE series_code = ?",
                Long.class,
                "DASH-SERIES-" + suffix
        );
        long ownerOrgId = jdbcTemplate.queryForObject(
                "SELECT id FROM organization WHERE deleted = 0 ORDER BY id LIMIT 1",
                Long.class
        );

        for (int hour = 1; hour <= 6; hour++) {
            insertEdition(
                    contestId,
                    ownerOrgId,
                    "开放报名" + hour + "-" + suffix,
                    "SCHOOL",
                    1,
                    REFERENCE_TIME.plusHours(hour)
            );
        }
        insertEdition(contestId, ownerOrgId, "已经截止-" + suffix, "PROVINCE", 1, REFERENCE_TIME.minusSeconds(1));
        insertEdition(contestId, ownerOrgId, "七天之外-" + suffix, "NATIONAL", 1, REFERENCE_TIME.plusDays(8));
        insertEdition(contestId, ownerOrgId, "草稿届次-" + suffix, "SCHOOL", 0, REFERENCE_TIME.plusDays(2));
        insertEdition(contestId, ownerOrgId, "提交阶段-" + suffix, "PROVINCE", 2, REFERENCE_TIME.plusDays(3));
        sqlSessionTemplate.clearCache();

        assertThat(dashboardMapper.countContestSeries()).isEqualTo(seriesBefore + 1);
        assertThat(dashboardMapper.countPublishedEditions()).isEqualTo(publishedBefore + 9);
        assertThat(dashboardMapper.countRegisteringEditions(REFERENCE_TIME)).isEqualTo(registeringBefore + 7);
        Map<String, Long> levelsAfter = dashboardMapper.selectLevelDistribution().stream()
                .collect(Collectors.toMap(DashboardLevelCountRow::level, DashboardLevelCountRow::count));
        assertThat(levelsAfter.get("SCHOOL")).isEqualTo(levelsBefore.getOrDefault("SCHOOL", 0L) + 7);
        assertThat(levelsAfter.get("PROVINCE")).isEqualTo(levelsBefore.getOrDefault("PROVINCE", 0L) + 2);
        assertThat(levelsAfter.get("NATIONAL")).isEqualTo(levelsBefore.getOrDefault("NATIONAL", 0L) + 1);

        List<DashboardDeadlineRow> deadlines = dashboardMapper.selectUpcomingDeadlines(
                REFERENCE_TIME,
                REFERENCE_TIME.plusDays(7),
                5
        );
        assertThat(deadlines).hasSize(5);
        assertThat(deadlines).extracting(DashboardDeadlineRow::editionName)
                .allMatch(name -> name.startsWith("开放报名"));
        assertThat(deadlines).extracting(DashboardDeadlineRow::registerDeadline)
                .containsExactly(
                        REFERENCE_TIME.plusHours(1),
                        REFERENCE_TIME.plusHours(2),
                        REFERENCE_TIME.plusHours(3),
                        REFERENCE_TIME.plusHours(4),
                        REFERENCE_TIME.plusHours(5)
                );
    }

    private long requiredProvinceDictionary() {
        List<Long> ids = jdbcTemplate.queryForList(
                """
                SELECT id FROM sys_dict_type
                WHERE dict_code = 'CN_PROVINCE' AND status = 1 AND deleted = 0
                ORDER BY id LIMIT 1
                """,
                Long.class
        );
        assertThat(ids).as("CN_PROVINCE dictionary prerequisite").isNotEmpty();
        return ids.get(0);
    }

    private void insertSchool(String code, String name, String provinceCode, int status, int deleted) {
        jdbcTemplate.update(
                """
                INSERT INTO organization
                    (org_code, org_name, org_type, province_code, status, created_by, deleted, version)
                VALUES (?, ?, 'SCHOOL', ?, ?, 0, ?, 0)
                """,
                code,
                name,
                provinceCode,
                status,
                deleted
        );
    }

    private void insertUser(String username, int status, int deleted) {
        jdbcTemplate.update(
                """
                INSERT INTO sys_user
                    (username, real_name, password_hash, email, school_verification_status,
                     status, created_by, deleted, version)
                VALUES (?, '大屏测试用户', ?, ?, 'PENDING', ?, 0, ?, 0)
                """,
                username,
                TEST_PASSWORD_HASH,
                username + "@example.edu",
                status,
                deleted
        );
    }

    private void insertRoleGrant(
            long userId,
            long roleId,
            String scopeType,
            long scopeId,
            int status,
            LocalDateTime validFrom,
            LocalDateTime validTo
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO sys_user_role
                    (user_id, role_id, scope_type, scope_id, status, valid_from, valid_to, deleted, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0)
                """,
                userId,
                roleId,
                scopeType,
                scopeId,
                status,
                validFrom,
                validTo
        );
    }

    private void insertEdition(
            long contestId,
            long ownerOrgId,
            String name,
            String level,
            int status,
            LocalDateTime deadline
    ) {
        Long schoolId = switch (level) {
            case "SCHOOL" -> jdbcTemplate.queryForObject(
                    "SELECT id FROM organization WHERE org_type = 'SCHOOL' AND deleted = 0 ORDER BY id LIMIT 1",
                    Long.class
            );
            default -> null;
        };
        String provinceCode = "PROVINCE".equals(level) ? "510000" : null;
        jdbcTemplate.update(
                """
                INSERT INTO contest_edition
                    (contest_id, owner_org_id, edition_name, year, level, school_id, province_code, status,
                     register_deadline, created_by, deleted, version)
                VALUES (?, ?, ?, 2076, ?, ?, ?, ?, ?, 0, 0, 0)
                """,
                contestId,
                ownerOrgId,
                name,
                level,
                schoolId,
                provinceCode,
                status,
                deadline
        );
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @MapperScan("com.campus.contest.module.dashboard.mapper")
    @Import(MybatisPlusConfig.class)
    static class TestApplication {
    }
}
