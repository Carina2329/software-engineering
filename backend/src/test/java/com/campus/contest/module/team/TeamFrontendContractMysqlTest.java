package com.campus.contest.module.team;

import com.campus.contest.config.MybatisPlusConfig;
import com.campus.contest.module.team.mapper.AvailableTeacherMapper;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_TEAM_REGISTRATION_MYSQL_TESTS", matches = "true")
@SpringBootTest(
        classes = TeamFrontendContractMysqlTest.TestApplication.class,
        properties = {
                "spring.main.web-application-type=none",
                "spring.datasource.url=${TEAM_REGISTRATION_TEST_DB_URL:jdbc:mysql://localhost:3306/contest_db_dashboard_e2e?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false}",
                "spring.datasource.username=${TEAM_REGISTRATION_TEST_DB_USERNAME:root}",
                "spring.datasource.password=${TEAM_REGISTRATION_TEST_DB_PASSWORD:root123}",
                "mybatis-plus.configuration.local-cache-scope=STATEMENT"
        }
)
@Transactional
class TeamFrontendContractMysqlTest {

    @Autowired
    private AvailableTeacherMapper availableTeacherMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void teacherLookupUsesRealMysql() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Long schoolId = jdbcTemplate.queryForObject(
                "SELECT id FROM organization WHERE org_type = 'SCHOOL' AND status = 1 AND deleted = 0 LIMIT 1",
                Long.class
        );
        Long teacherRoleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE role_code = 'TEACHER' AND deleted = 0 LIMIT 1",
                Long.class
        );
        String username = "front-contract-" + suffix;
        String realName = "契约教师" + suffix;

        jdbcTemplate.update(
                """
                INSERT INTO sys_user
                    (username, real_name, password_hash, email, school_id,
                     school_verification_status, college, title, status,
                     created_at, created_by, updated_at, updated_by, deleted, version)
                VALUES (?, ?, 'x', ?, ?, 'VERIFIED', '计算机学院', '副教授', 1,
                        NOW(), 1, NOW(), 1, 0, 0)
                """,
                username,
                realName,
                username + "@example.test",
                schoolId
        );
        Long teacherId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?",
                Long.class,
                username
        );
        jdbcTemplate.update(
                """
                INSERT INTO sys_user_role
                    (user_id, role_id, scope_type, scope_id, status, updated_by, deleted, version)
                VALUES (?, ?, 'GLOBAL', 0, 1, 1, 0, 0)
                """,
                teacherId,
                teacherRoleId
        );

        assertThat(availableTeacherMapper.count(suffix)).isEqualTo(1);
        assertThat(availableTeacherMapper.selectPage(suffix, 0, 20))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.id()).isEqualTo(teacherId);
                    assertThat(row.realName()).isEqualTo(realName);
                    assertThat(row.schoolId()).isEqualTo(schoolId);
                    assertThat(row.schoolName()).isNotBlank();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @MapperScan("com.campus.contest.module.team.mapper")
    @Import(MybatisPlusConfig.class)
    static class TestApplication {
    }
}
