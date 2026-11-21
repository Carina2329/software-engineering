package com.campus.contest.module.contest;

import com.campus.contest.module.contest.mapper.ContestAccessCriteria;
import com.campus.contest.module.contest.mapper.ContestAccessSqlProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContestAccessSqlProviderTest {

    private final ContestAccessSqlProvider provider = new ContestAccessSqlProvider();

    @Test
    @DisplayName("普通用户 SQL 只允许已发布且匹配学校、省份、公开或定向赛事的数据")
    void ordinaryAccessUsesVisibilityScope() {
        ContestAccessCriteria criteria = new ContestAccessCriteria();
        criteria.setManager(false);
        criteria.setViewerSchoolId(10L);
        criteria.setViewerProvinceCode(510000L);
        criteria.setEventIds(List.of(31L));
        criteria.setContestId(1L);

        String sql = provider.selectEditionList(criteria);

        assertThat(sql).contains("e.status <> 0");
        assertThat(sql).contains("e.visibility = 'PUBLIC'");
        assertThat(sql).contains("e.visibility = 'SCHOOL'");
        assertThat(sql).contains("e.school_id = #{criteria.viewerSchoolId}");
        assertThat(sql).contains("e.visibility = 'PROVINCE'");
        assertThat(sql).contains("e.visibility = 'UNLISTED'");
        assertThat(sql).contains("e.id IN (#{criteria.eventIds[0]})");
    }

    @Test
    @DisplayName("非全局管理员 SQL 只能访问授权组织、省份或具体赛事")
    void managerAccessUsesGrantedScopes() {
        ContestAccessCriteria criteria = new ContestAccessCriteria();
        criteria.setManager(true);
        criteria.setOrganizationIds(List.of(10L, 11L));
        criteria.setProvinceCodes(List.of(510000L));
        criteria.setEventIds(List.of(31L));

        String sql = provider.selectEditionList(criteria);

        assertThat(sql).contains("e.owner_org_id IN");
        assertThat(sql).contains("e.province_code IN");
        assertThat(sql).contains("e.id IN");
        assertThat(sql).contains("#{criteria.organizationIds[0]}");
        assertThat(sql).contains("#{criteria.provinceCodes[0]}");
        assertThat(sql).contains("#{criteria.eventIds[0]}");
    }

    @Test
    @DisplayName("全局管理员不附加组织范围限制")
    void globalManagerHasNoDataScopeRestriction() {
        ContestAccessCriteria criteria = new ContestAccessCriteria();
        criteria.setManager(true);
        criteria.setGlobal(true);

        assertThat(provider.selectContestPage(criteria)).contains("1 = 1");
        assertThat(provider.selectEditionList(criteria)).contains("1 = 1");
    }
}
