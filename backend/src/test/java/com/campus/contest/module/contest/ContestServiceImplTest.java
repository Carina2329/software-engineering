package com.campus.contest.module.contest;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.contest.TestInfrastructure;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.contest.dto.ContestCreateRequest;
import com.campus.contest.module.contest.dto.ContestUpdateRequest;
import com.campus.contest.module.contest.entity.ContestDO;
import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.mapper.ContestAccessCriteria;
import com.campus.contest.module.contest.mapper.ContestAccessMapper;
import com.campus.contest.module.contest.mapper.ContestMapper;
import com.campus.contest.module.contest.service.ContestAuditService;
import com.campus.contest.module.contest.service.ContestAuthorizationService;
import com.campus.contest.module.contest.service.impl.ContestServiceImpl;
import com.campus.contest.module.contest.vo.ContestCreateVO;
import com.campus.contest.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContestServiceImplTest {

    private static final Long OPERATOR_ID = 7L;

    @Mock
    private ContestMapper contestMapper;
    @Mock
    private ContestAccessMapper accessMapper;
    @Mock
    private ContestAuthorizationService authorizationService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ContestAuditService auditService;

    private ContestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ContestServiceImpl(
                contestMapper,
                accessMapper,
                authorizationService,
                TestInfrastructure.passthroughIdempotencyService(),
                currentUserProvider,
                auditService,
                TestInfrastructure.transactionTemplate()
        );
        lenient().when(currentUserProvider.requireUserId()).thenReturn(OPERATOR_ID);
        lenient().when(authorizationService.currentAccessCriteria())
                .thenReturn(accessCriteria());
    }

    @Test
    @DisplayName("平台管理员创建稳定系列编码，竞赛名称仍允许重复")
    void platformAdminCreatesContestSeries() {
        when(contestMapper.selectCount(any())).thenReturn(0L);
        when(contestMapper.insert(any(ContestDO.class))).thenAnswer(invocation -> {
            ContestDO entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        });

        ContestCreateVO result = service.create(createRequest(), "contest-create-key");

        assertThat(result.id()).isEqualTo("100");
        assertThat(result.contestName()).isEqualTo("程序设计竞赛");
        ArgumentCaptor<ContestDO> captor = ArgumentCaptor.forClass(ContestDO.class);
        verify(contestMapper).insert(captor.capture());
        assertThat(captor.getValue().getSeriesCode()).isEqualTo("SOFTWARE_ENGINEERING");
        verify(authorizationService).requirePlatformAdmin();
        verify(auditService).record(eq(OPERATOR_ID), eq("CONTEST_CREATE"), any());
    }

    @Test
    @DisplayName("竞赛系列编码重复返回 409")
    void duplicateSeriesCodeIsRejected() {
        when(contestMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(createRequest(), "contest-create-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.DUPLICATE_RELATION.getCode()));
        verify(contestMapper, never()).insert(any(ContestDO.class));
    }

    @Test
    @DisplayName("组织管理员不能创建官方竞赛系列")
    void organizationManagerCannotCreateSeries() {
        doThrow(new BizException(
                ErrorCode.DATA_FORBIDDEN.getCode(),
                "只有平台管理员可以维护竞赛系列"
        )).when(authorizationService).requirePlatformAdmin();

        assertThatThrownBy(() -> service.create(createRequest(), "contest-create-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.DATA_FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("版本落后返回 409，且不写审计")
    void updateContestRejectsStaleVersion() {
        ContestDO existing = contest(11L, "蓝桥杯", "BLUE_BRIDGE", "PROVINCE", 3);
        when(contestMapper.selectById(11L)).thenReturn(existing);

        ContestUpdateRequest request = new ContestUpdateRequest();
        request.setOrganizer("省教育厅");
        request.setVersion(2);

        assertThatThrownBy(() -> service.update(11L, request, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode()));
        verify(contestMapper, never()).updateById(any(ContestDO.class));
        verify(auditService, never()).record(any(), anyString(), any());
    }

    @Test
    @DisplayName("EP-013 返回当前版本，旧版本冲突、新版本成功")
    void queriedVersionCanUpdateContest() {
        ContestDO existing = contest(11L, "蓝桥杯", "BLUE_BRIDGE", "PROVINCE", 4);
        ContestEditionDO edition = new ContestEditionDO();
        edition.setEditionName("2026 省赛");
        when(accessMapper.selectContestById(any())).thenReturn(existing);
        when(accessMapper.selectEditionList(any())).thenReturn(List.of(edition));
        when(contestMapper.selectById(11L)).thenReturn(existing);
        when(contestMapper.updateById(any(ContestDO.class))).thenReturn(1);

        int version = service.get(11L).version();
        assertThat(version).isEqualTo(4);

        ContestUpdateRequest stale = new ContestUpdateRequest();
        stale.setOrganizer("旧版本");
        stale.setVersion(3);
        assertThatThrownBy(() -> service.update(11L, stale, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode()));

        ContestUpdateRequest current = new ContestUpdateRequest();
        current.setOrganizer("省教育厅");
        current.setVersion(version);
        assertThat(service.update(11L, current, null).version()).isEqualTo(5);
    }

    @Test
    @DisplayName("竞赛列表查询使用 OrganizationScopeService 生成的访问条件")
    void pageUsesAccessCriteria() {
        IPage<ContestDO> page = new Page<>(1, 20);
        page.setRecords(List.of(contest(1L, "系列", "SERIES", "NATIONAL", 0)));
        page.setTotal(1);
        when(accessMapper.selectContestPage(any(), any())).thenReturn(page);

        assertThat(service.page(null).total()).isEqualTo(1);

        ArgumentCaptor<ContestAccessCriteria> captor =
                ArgumentCaptor.forClass(ContestAccessCriteria.class);
        verify(accessMapper).selectContestPage(any(), captor.capture());
        assertThat(captor.getValue().isManager()).isTrue();
        assertThat(captor.getValue().isGlobal()).isTrue();
    }

    private ContestAccessCriteria accessCriteria() {
        ContestAccessCriteria criteria = new ContestAccessCriteria();
        criteria.setManager(true);
        criteria.setGlobal(true);
        return criteria;
    }

    private ContestCreateRequest createRequest() {
        ContestCreateRequest request = new ContestCreateRequest();
        request.setContestName("程序设计竞赛");
        request.setSeriesCode("SOFTWARE_ENGINEERING");
        request.setLevel("NATIONAL");
        request.setOrganizer("竞赛组委会");
        return request;
    }

    private ContestDO contest(
            Long id,
            String name,
            String seriesCode,
            String level,
            int version
    ) {
        ContestDO contest = new ContestDO();
        contest.setId(id);
        contest.setContestName(name);
        contest.setSeriesCode(seriesCode);
        contest.setLevel(level);
        contest.setOrganizer("原主办方");
        contest.setVersion(version);
        return contest;
    }
}
