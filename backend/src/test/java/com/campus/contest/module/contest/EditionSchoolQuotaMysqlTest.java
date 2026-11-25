package com.campus.contest.module.contest;

import com.campus.contest.config.MybatisPlusConfig;
import com.campus.contest.module.contest.entity.EditionSchoolQuotaDO;
import com.campus.contest.module.contest.mapper.EditionSchoolQuotaMapper;
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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_TEAM_REGISTRATION_MYSQL_TESTS", matches = "true")
@SpringBootTest(
        classes = EditionSchoolQuotaMysqlTest.TestApplication.class,
        properties = {
                "spring.main.web-application-type=none",
                "spring.datasource.url=${TEAM_REGISTRATION_TEST_DB_URL:jdbc:mysql://localhost:3306/contest_db_dashboard_e2e?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false}",
                "spring.datasource.username=${TEAM_REGISTRATION_TEST_DB_USERNAME:root}",
                "spring.datasource.password=${TEAM_REGISTRATION_TEST_DB_PASSWORD:root123}",
                "mybatis-plus.configuration.local-cache-scope=STATEMENT"
        }
)
@Transactional
class EditionSchoolQuotaMysqlTest {

    @Autowired
    private EditionSchoolQuotaMapper quotaMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void unlimitedUpdatePreservesUsageAndSoftDeletedRowCanBeReactivated() {
        long suffix = Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L);
        long firstSchoolId = 1_000_000_000L + suffix;
        long secondSchoolId = firstSchoolId + 1;
        LocalDateTime now = LocalDateTime.of(2026, 11, 25, 20, 0);

        EditionSchoolQuotaDO first = newQuota(firstSchoolId, 2, 1, now);
        assertThat(quotaMapper.insert(first)).isEqualTo(1);
        assertThat(quotaMapper.updateMaxTeams(first.getId(), null, 9L, now.plusMinutes(1)))
                .isEqualTo(1);

        Integer firstUsed = jdbcTemplate.queryForObject(
                "SELECT used_teams FROM edition_school_quota WHERE id = ?",
                Integer.class,
                first.getId()
        );
        Integer firstMax = jdbcTemplate.queryForObject(
                "SELECT max_teams FROM edition_school_quota WHERE id = ?",
                Integer.class,
                first.getId()
        );
        assertThat(firstUsed).isEqualTo(1);
        assertThat(firstMax).isNull();

        EditionSchoolQuotaDO second = newQuota(secondSchoolId, 3, 0, now);
        assertThat(quotaMapper.insert(second)).isEqualTo(1);
        assertThat(quotaMapper.softDeleteIfUnused(second.getId(), 9L, now.plusMinutes(2)))
                .isEqualTo(1);
        assertThat(quotaMapper.selectByEditionForUpdate(1L))
                .filteredOn(row -> row.getSchoolId().equals(secondSchoolId))
                .singleElement()
                .extracting(EditionSchoolQuotaDO::getDeleted)
                .isEqualTo(1);

        assertThat(quotaMapper.updateMaxTeams(second.getId(), 4, 9L, now.plusMinutes(3)))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap(
                """
                SELECT max_teams, used_teams, deleted
                  FROM edition_school_quota
                 WHERE id = ?
                """,
                second.getId()
        )).containsEntry("max_teams", 4)
                .containsEntry("used_teams", 0)
                .containsEntry("deleted", 0);
    }

    private EditionSchoolQuotaDO newQuota(
            Long schoolId,
            Integer maxTeams,
            Integer usedTeams,
            LocalDateTime now
    ) {
        EditionSchoolQuotaDO row = new EditionSchoolQuotaDO();
        row.setEditionId(1L);
        row.setSchoolId(schoolId);
        row.setMaxTeams(maxTeams);
        row.setUsedTeams(usedTeams);
        row.setCreatedAt(now);
        row.setCreatedBy(9L);
        row.setUpdatedAt(now);
        row.setUpdatedBy(9L);
        row.setDeleted(0);
        row.setVersion(0);
        return row;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @MapperScan("com.campus.contest.module.contest.mapper")
    @Import(MybatisPlusConfig.class)
    static class TestApplication {
    }
}
