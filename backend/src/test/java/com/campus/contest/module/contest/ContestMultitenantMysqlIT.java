package com.campus.contest.module.contest;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.contest.config.MybatisPlusConfig;
import com.campus.contest.module.contest.entity.ContestDO;
import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.mapper.ContestAccessCriteria;
import com.campus.contest.module.contest.mapper.ContestAccessMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用公共多组织测试库验证真实 MyBatis 范围 SQL。
 */
@EnabledIfEnvironmentVariable(named = "RUN_CONTEST_MYSQL_TESTS", matches = "true")
@SpringBootTest(classes = ContestMultitenantMysqlIT.TestApplication.class, properties = {
        "spring.main.web-application-type=none",
        "spring.datasource.url=jdbc:mysql://localhost:3306/contest_mt_test"
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false",
        "spring.datasource.username=root",
        "spring.datasource.password=root123",
        "mybatis-plus.configuration.map-underscore-to-camel-case=true"
})
class ContestMultitenantMysqlIT {

    @Autowired
    private ContestAccessMapper accessMapper;

    @Test
    @DisplayName("A 校管理员真实 SQL 只查询到 A 校比赛")
    void schoolAdminOnlySeesOwnSchoolEdition() {
        ContestAccessCriteria criteria = managerCriteria();
        criteria.setContestId(1L);

        assertThat(accessMapper.selectEditionPage(new Page<>(1, 100), criteria).getRecords())
                .extracting(ContestEditionDO::getEditionName)
                .contains("2026年A大学统计建模校赛")
                .doesNotContain(
                        "2026年B大学统计建模校赛",
                        "2026年D大学统计建模校赛"
                );
    }

    @Test
    @DisplayName("学校管理员可以发现尚无本校届次的竞赛系列")
    void managerCanDiscoverSeriesWithoutOwnEdition() {
        ContestAccessCriteria criteria = managerCriteria();

        assertThat(accessMapper.selectContestPage(new Page<>(1, 100), criteria).getRecords())
                .extracting(ContestDO::getSeriesCode)
                .contains("SERIES-STAT-MODEL", "SERIES-SICHUAN-SOFT");
    }

    @Test
    @DisplayName("四川管理员真实 SQL 只查询到四川省赛")
    void provinceAdminOnlySeesProvinceEditions() {
        ContestAccessCriteria criteria = new ContestAccessCriteria();
        criteria.setManager(true);
        criteria.setProvinceCodes(List.of(510000L));
        criteria.setContestId(1L);

        assertThat(accessMapper.selectEditionPage(new Page<>(1, 100), criteria).getRecords())
                .extracting(ContestEditionDO::getEditionName)
                .contains("2026年四川省统计建模省赛")
                .doesNotContain(
                        "2026年A大学统计建模校赛",
                        "2026年D大学统计建模校赛"
                );
    }

    @Test
    @DisplayName("四川学生只看到本校、本省和全国公开赛")
    void sichuanStudentVisibilityMatchesScope() {
        ContestAccessCriteria criteria = new ContestAccessCriteria();
        criteria.setManager(false);
        criteria.setViewerSchoolId(2L);
        criteria.setViewerProvinceCode(510000L);
        criteria.setContestId(1L);

        assertThat(accessMapper.selectEditionPage(new Page<>(1, 100), criteria).getRecords())
                .extracting(ContestEditionDO::getEditionName)
                .contains(
                        "2026年A大学统计建模校赛",
                        "2026年四川省统计建模省赛",
                        "2026年全国统计建模决赛"
                )
                .doesNotContain(
                        "2026年B大学统计建模校赛",
                        "2026年D大学统计建模校赛"
                );
    }

    @Test
    @DisplayName("湖南学生只看到湖南校赛和全国公开赛")
    void hunanStudentVisibilityMatchesScope() {
        ContestAccessCriteria criteria = new ContestAccessCriteria();
        criteria.setManager(false);
        criteria.setViewerSchoolId(5L);
        criteria.setViewerProvinceCode(430000L);
        criteria.setContestId(1L);

        assertThat(accessMapper.selectEditionPage(new Page<>(1, 100), criteria).getRecords())
                .extracting(ContestEditionDO::getEditionName)
                .contains(
                        "2026年D大学统计建模校赛",
                        "2026年全国统计建模决赛"
                )
                .doesNotContain(
                        "2026年A大学统计建模校赛",
                        "2026年四川省统计建模省赛"
                );
    }

    private ContestAccessCriteria managerCriteria() {
        ContestAccessCriteria criteria = new ContestAccessCriteria();
        criteria.setManager(true);
        criteria.setOrganizationIds(List.of(2L));
        return criteria;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @MapperScan("com.campus.contest.module.contest.mapper")
    @Import(MybatisPlusConfig.class)
    static class TestApplication {
    }
}
