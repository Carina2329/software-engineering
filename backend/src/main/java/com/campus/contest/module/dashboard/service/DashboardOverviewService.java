package com.campus.contest.module.dashboard.service;

import com.campus.contest.module.dashboard.dto.DashboardDeadlineRow;
import com.campus.contest.module.dashboard.dto.DashboardLevelCountRow;
import com.campus.contest.module.dashboard.mapper.DashboardMapper;
import com.campus.contest.module.dashboard.vo.DashboardContestOperationsVO;
import com.campus.contest.module.dashboard.vo.DashboardDeadlineVO;
import com.campus.contest.module.dashboard.vo.DashboardLevelDistributionVO;
import com.campus.contest.module.dashboard.vo.DashboardOverviewVO;
import com.campus.contest.module.dashboard.vo.DashboardSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardOverviewService {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    public static final String DATA_SOURCE = "平台组织、用户及竞赛届次业务库";
    private static final List<String> LEVEL_ORDER = List.of("SCHOOL", "PROVINCE", "NATIONAL");
    private static final int UPCOMING_DEADLINE_LIMIT = 5;

    private final DashboardMapper dashboardMapper;
    private final DashboardAccessService accessService;
    private final Clock clock;

    public DashboardOverviewVO getCurrentOverview() {
        accessService.requireCurrentAccess();
        return buildSnapshot();
    }

    public DashboardOverviewVO buildSnapshot() {
        Instant nowInstant = clock.instant();
        LocalDateTime now = LocalDateTime.ofInstant(nowInstant, BUSINESS_ZONE);
        OffsetDateTime generatedAt = nowInstant.atZone(BUSINESS_ZONE)
                .toOffsetDateTime()
                .truncatedTo(ChronoUnit.SECONDS);

        DashboardSummaryVO summary = new DashboardSummaryVO(
                dashboardMapper.countEffectiveSchools(),
                dashboardMapper.countActiveUsers(),
                dashboardMapper.countRegisteringEditions(now),
                dashboardMapper.countPendingAffiliations()
        );
        DashboardContestOperationsVO contestOperations = new DashboardContestOperationsVO(
                dashboardMapper.countContestSeries(),
                dashboardMapper.countPublishedEditions(),
                levelDistribution(dashboardMapper.selectLevelDistribution())
        );
        List<DashboardDeadlineVO> deadlines = dashboardMapper.selectUpcomingDeadlines(
                        now,
                        now.plusDays(7),
                        UPCOMING_DEADLINE_LIMIT
                ).stream()
                .map(this::toDeadline)
                .toList();

        return new DashboardOverviewVO(
                generatedAt,
                DATA_SOURCE,
                summary,
                contestOperations,
                dashboardMapper.selectProvinceSchools(),
                dashboardMapper.selectUserRoles(now),
                deadlines
        );
    }

    private List<DashboardLevelDistributionVO> levelDistribution(List<DashboardLevelCountRow> rows) {
        Map<String, DashboardLevelCountRow> byLevel = rows == null
                ? Map.of()
                : rows.stream().collect(Collectors.toMap(
                        DashboardLevelCountRow::level,
                        Function.identity(),
                        (left, right) -> left
                ));
        return LEVEL_ORDER.stream()
                .map(level -> new DashboardLevelDistributionVO(
                        level,
                        byLevel.containsKey(level) ? byLevel.get(level).count() : 0
                ))
                .toList();
    }

    private DashboardDeadlineVO toDeadline(DashboardDeadlineRow row) {
        return new DashboardDeadlineVO(
                String.valueOf(row.editionId()),
                row.editionName(),
                row.contestName(),
                row.level(),
                row.registerDeadline().atZone(BUSINESS_ZONE).toOffsetDateTime()
        );
    }
}
