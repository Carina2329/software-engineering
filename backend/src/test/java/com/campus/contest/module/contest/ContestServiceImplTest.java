package com.campus.contest.module.contest;

import com.campus.contest.TestInfrastructure;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.contest.dto.ContestCreateRequest;
import com.campus.contest.module.contest.dto.ContestUpdateRequest;
import com.campus.contest.module.contest.entity.ContestDO;
import com.campus.contest.module.contest.mapper.ContestEditionMapper;
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
    private ContestEditionMapper editionMapper;
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
                editionMapper,
                authorizationService,
                TestInfrastructure.passthroughIdempotencyService(),
                currentUserProvider,
                auditService,
                TestInfrastructure.transactionTemplate()
        );
        lenient().when(currentUserProvider.requireUserId()).thenReturn(OPERATOR_ID);
    }

    @Test
    @DisplayName("竞赛名称允许重复，创建成功写入主档与审计")
    void duplicateContestNamesAreAllowed() {
        when(contestMapper.insert(any(ContestDO.class))).thenAnswer(invocation -> {
            ContestDO entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        });

        ContestCreateRequest request = new ContestCreateRequest();
        request.setContestName("程序设计竞赛");
        request.setLevel("SCHOOL");
        request.setOrganizer("计算机学院");

        ContestCreateVO result = service.create(request, "contest-create-key");

        assertThat(result.id()).isEqualTo("100");
        assertThat(result.contestName()).isEqualTo("程序设计竞赛");
        verify(contestMapper, never()).selectCount(any());
        verify(auditService).record(eq(OPERATOR_ID), eq("CONTEST_CREATE"), any());
    }

    @Test
    @DisplayName("创建竞赛要求 Idempotency-Key")
    void createContestRequiresIdempotencyKey() {
        ContestCreateRequest request = new ContestCreateRequest();
        request.setContestName("数学建模竞赛");
        request.setLevel("NATIONAL");

        assertThatThrownBy(() -> service.create(request, " "))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
        verify(contestMapper, never()).insert(any(ContestDO.class));
    }

    @Test
    @DisplayName("竞赛版本落后返回 409，且不写审计")
    void updateContestRejectsStaleVersion() {
        ContestDO existing = contest(11L, "蓝桥杯", "PROVINCE", 3);
        when(contestMapper.selectById(11L)).thenReturn(existing);

        ContestUpdateRequest request = new ContestUpdateRequest();
        request.setOrganizer("省教育厅");
        request.setVersion(2);

        assertThatThrownBy(() -> service.update(11L, request, "contest-update-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode()));
        verify(contestMapper, never()).updateById(any(ContestDO.class));
        verify(auditService, never()).record(any(), anyString(), any());
    }

    @Test
    @DisplayName("修改竞赛返回新版本号并保留未提交字段")
    void updateContestReturnsNewVersion() {
        ContestDO existing = contest(11L, "蓝桥杯", "PROVINCE", 3);
        when(contestMapper.selectById(11L)).thenReturn(existing);
        when(contestMapper.updateById(any(ContestDO.class))).thenReturn(1);

        ContestUpdateRequest request = new ContestUpdateRequest();
        request.setOrganizer("省教育厅");
        request.setVersion(3);

        assertThat(service.update(11L, request, "contest-update-key").version()).isEqualTo(4);

        ArgumentCaptor<ContestDO> captor = ArgumentCaptor.forClass(ContestDO.class);
        verify(contestMapper).updateById(captor.capture());
        assertThat(captor.getValue().getContestName()).isNull();
        assertThat(captor.getValue().getOrganizer()).isEqualTo("省教育厅");
    }

    @Test
    @DisplayName("EP-013 返回当前版本，使用该版本修改竞赛成功")
    void queriedVersionCanUpdateContest() {
        ContestDO existing = contest(11L, "蓝桥杯", "PROVINCE", 4);
        when(contestMapper.selectById(11L)).thenReturn(existing);
        when(editionMapper.selectList(any())).thenReturn(List.of());
        when(authorizationService.canManage()).thenReturn(true);
        when(contestMapper.updateById(any(ContestDO.class))).thenReturn(1);

        int version = service.get(11L).version();
        ContestUpdateRequest request = new ContestUpdateRequest();
        request.setOrganizer("省教育厅");
        request.setVersion(version);

        assertThat(service.update(11L, request, "query-version-update-key").version())
                .isEqualTo(5);
    }

    private ContestDO contest(Long id, String name, String level, int version) {
        ContestDO contest = new ContestDO();
        contest.setId(id);
        contest.setContestName(name);
        contest.setLevel(level);
        contest.setOrganizer("原主办方");
        contest.setVersion(version);
        return contest;
    }
}
