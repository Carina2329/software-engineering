package com.campus.contest.module.team;

import com.campus.contest.config.MybatisPlusConfig;
import com.campus.contest.module.team.entity.EditionEntryGrantDO;
import com.campus.contest.module.team.mapper.EditionEntryGrantMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "RUN_TEAM_REGISTRATION_MYSQL_TESTS", matches = "true")
@SpringBootTest(
        classes = EditionEntryGrantMysqlTest.TestApplication.class,
        properties = {
                "spring.main.web-application-type=none",
                "spring.datasource.url=${TEAM_REGISTRATION_TEST_DB_URL:jdbc:mysql://localhost:3306/contest_db_dashboard_e2e?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false}",
                "spring.datasource.username=${TEAM_REGISTRATION_TEST_DB_USERNAME:root}",
                "spring.datasource.password=${TEAM_REGISTRATION_TEST_DB_PASSWORD:root123}",
                "mybatis-plus.configuration.local-cache-scope=STATEMENT"
        }
)
@Transactional
class EditionEntryGrantMysqlTest {

    @Autowired
    private EditionEntryGrantMapper entryGrantMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void activeGrantIsUniqueAndCanBeReimportedAfterRevoke() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        long schoolId = 900_000_000L + Math.abs(suffix.hashCode());
        String studentNo = "EG" + suffix;
        LocalDateTime now = LocalDateTime.of(2026, 11, 25, 15, 30);

        EditionEntryGrantDO first = newGrant(schoolId, studentNo, now);
        assertThat(entryGrantMapper.insert(first)).isEqualTo(1);
        assertThat(first.getId()).isNotNull();

        EditionEntryGrantDO duplicate = newGrant(schoolId, studentNo, now);
        assertThatThrownBy(() -> entryGrantMapper.insert(duplicate))
                .isInstanceOf(DuplicateKeyException.class);

        EditionEntryGrantDO locked = entryGrantMapper.selectActiveByKeyForUpdate(
                1L, schoolId, studentNo
        );
        assertThat(locked).isNotNull();
        assertThat(locked.getId()).isEqualTo(first.getId());

        locked.setStatus("REVOKED");
        locked.setReason("MySQL revoke assertion");
        locked.setUpdatedAt(now);
        locked.setUpdatedBy(1L);
        locked.setRevokedAt(now);
        locked.setRevokedBy(1L);
        assertThat(entryGrantMapper.revokeActiveRecord(locked)).isEqualTo(1);

        EditionEntryGrantDO reimported = newGrant(schoolId, studentNo, now.plusMinutes(1));
        assertThat(entryGrantMapper.insert(reimported)).isEqualTo(1);
        assertThat(entryGrantMapper.selectActiveByKeyForUpdate(1L, schoolId, studentNo))
                .extracting(EditionEntryGrantDO::getId)
                .isEqualTo(reimported.getId());

        Integer total = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM edition_entry_grant
                 WHERE target_edition_id = 1
                   AND school_id = ?
                   AND student_no = ?
                """,
                Integer.class,
                schoolId,
                studentNo
        );
        assertThat(total).isEqualTo(2);
        Integer revoked = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM edition_entry_grant
                 WHERE target_edition_id = 1
                   AND school_id = ?
                   AND student_no = ?
                   AND status = 'REVOKED'
                """,
                Integer.class,
                schoolId,
                studentNo
        );
        assertThat(revoked).isEqualTo(1);
    }

    private EditionEntryGrantDO newGrant(long schoolId, String studentNo, LocalDateTime now) {
        EditionEntryGrantDO record = new EditionEntryGrantDO();
        record.setTargetEditionId(1L);
        record.setGrantType("QUALIFIED");
        record.setLeaderName("MySQL Grant");
        record.setSchoolId(schoolId);
        record.setStudentNo(studentNo);
        record.setStatus("ACTIVE");
        record.setCreatedAt(now);
        record.setCreatedBy(1L);
        record.setUpdatedAt(now);
        record.setUpdatedBy(1L);
        record.setDeleted(0);
        record.setVersion(0);
        return record;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @MapperScan("com.campus.contest.module.team.mapper")
    @Import(MybatisPlusConfig.class)
    static class TestApplication {
    }
}
