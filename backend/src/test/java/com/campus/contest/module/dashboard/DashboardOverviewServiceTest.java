package com.campus.contest.module.dashboard;

import com.campus.contest.module.dashboard.dto.DashboardDeadlineRow;
import com.campus.contest.module.dashboard.dto.DashboardLevelCountRow;
import com.campus.contest.module.dashboard.mapper.DashboardMapper;
import com.campus.contest.module.dashboard.service.DashboardAccessService;
import com.campus.contest.module.dashboard.service.DashboardOverviewService;
import com.campus.contest.module.dashboard.vo.DashboardLevelDistributionVO;
import com.campus.contest.module.dashboard.vo.DashboardOverviewVO;
import com.campus.contest.module.dashboard.vo.DashboardProvinceSchoolVO;
import com.campus.contest.module.dashboard.vo.DashboardUserRoleVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardOverviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-21T09:30:00Z");
    private static final LocalDateTime BUSINESS_NOW = LocalDateTime.of(2026, 11, 21, 17, 30);

    private DashboardMapper mapper;
    private DashboardAccessService accessService;
    private DashboardOverviewService service;

    @BeforeEach
    void setUp() {
        mapper = mock(DashboardMapper.class);
        accessService = mock(DashboardAccessService.class);
        service = new DashboardOverviewService(
                mapper,
                accessService,
                Clock.fixed(NOW, DashboardOverviewService.BUSINESS_ZONE)
        );
    }

    @Test
    void administratorReceivesCompleteSnapshotWithContractOrdering() {
        when(mapper.countEffectiveSchools()).thenReturn(4L);
        when(mapper.countActiveUsers()).thenReturn(9L);
        when(mapper.countRegisteringEditions(BUSINESS_NOW)).thenReturn(6L);
        when(mapper.countPendingAffiliations()).thenReturn(2L);
        when(mapper.countContestSeries()).thenReturn(2L);
        when(mapper.countPublishedEditions()).thenReturn(6L);
        when(mapper.selectLevelDistribution()).thenReturn(List.of(
                new DashboardLevelCountRow("NATIONAL", 1L),
                new DashboardLevelCountRow("PROVINCE", 2L),
                new DashboardLevelCountRow("SCHOOL", 3L)
        ));
        when(mapper.selectProvinceSchools()).thenReturn(List.of(
                new DashboardProvinceSchoolVO("510000", "四川省", 3L),
                new DashboardProvinceSchoolVO("430000", "湖南省", 1L)
        ));
        when(mapper.selectUserRoles(BUSINESS_NOW)).thenReturn(List.of(
                new DashboardUserRoleVO("STUDENT", "学生", 3L)
        ));
        LocalDateTime deadline = BUSINESS_NOW.plusDays(2).withSecond(59);
        when(mapper.selectUpcomingDeadlines(
                BUSINESS_NOW,
                BUSINESS_NOW.plusDays(7),
                5
        )).thenReturn(List.of(new DashboardDeadlineRow(
                31L,
                "2026年校赛",
                "统计建模大赛",
                "SCHOOL",
                deadline
        )));

        DashboardOverviewVO overview = service.getCurrentOverview();

        verify(accessService).requireCurrentAccess();
        assertThat(overview.generatedAt().toInstant()).isEqualTo(NOW);
        assertThat(overview.generatedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(8));
        assertThat(overview.dataSource()).isEqualTo(DashboardOverviewService.DATA_SOURCE);
        assertThat(overview.summary().effectiveSchoolCount()).isEqualTo(4);
        assertThat(overview.summary().activeUserCount()).isEqualTo(9);
        assertThat(overview.summary().registeringEditionCount()).isEqualTo(6);
        assertThat(overview.summary().pendingAffiliationCount()).isEqualTo(2);
        assertThat(overview.contestOperations().contestSeriesCount()).isEqualTo(2);
        assertThat(overview.contestOperations().publishedEditionCount()).isEqualTo(6);
        assertThat(overview.contestOperations().levelDistribution())
                .extracting(DashboardLevelDistributionVO::level)
                .containsExactly("SCHOOL", "PROVINCE", "NATIONAL");
        assertThat(overview.contestOperations().levelDistribution())
                .extracting(DashboardLevelDistributionVO::count)
                .containsExactly(3L, 2L, 1L);
        assertThat(overview.provinceSchools()).hasSize(2);
        assertThat(overview.userRoles()).hasSize(1);
        assertThat(overview.upcomingDeadlines()).hasSize(1);
        assertThat(overview.upcomingDeadlines().get(0).editionId()).isEqualTo("31");
        assertThat(overview.upcomingDeadlines().get(0).registerDeadline().toLocalDateTime())
                .isEqualTo(deadline);
        assertThat(overview.upcomingDeadlines().get(0).registerDeadline().getOffset())
                .isEqualTo(ZoneOffset.ofHours(8));
    }

    @Test
    void emptyDatabaseReturnsZeroesAndEmptyArrays() {
        when(mapper.selectLevelDistribution()).thenReturn(List.of());
        when(mapper.selectProvinceSchools()).thenReturn(List.of());
        when(mapper.selectUserRoles(BUSINESS_NOW)).thenReturn(List.of());
        when(mapper.selectUpcomingDeadlines(
                eq(BUSINESS_NOW),
                eq(BUSINESS_NOW.plusDays(7)),
                eq(5)
        )).thenReturn(List.of());

        DashboardOverviewVO overview = service.buildSnapshot();

        assertThat(overview.summary().effectiveSchoolCount()).isZero();
        assertThat(overview.summary().activeUserCount()).isZero();
        assertThat(overview.summary().registeringEditionCount()).isZero();
        assertThat(overview.summary().pendingAffiliationCount()).isZero();
        assertThat(overview.contestOperations().contestSeriesCount()).isZero();
        assertThat(overview.contestOperations().publishedEditionCount()).isZero();
        assertThat(overview.contestOperations().levelDistribution())
                .extracting(DashboardLevelDistributionVO::count)
                .containsExactly(0L, 0L, 0L);
        assertThat(overview.provinceSchools()).isEmpty();
        assertThat(overview.userRoles()).isEmpty();
        assertThat(overview.upcomingDeadlines()).isEmpty();
        verify(mapper).selectUpcomingDeadlines(BUSINESS_NOW, BUSINESS_NOW.plusDays(7), 5);
    }
}
