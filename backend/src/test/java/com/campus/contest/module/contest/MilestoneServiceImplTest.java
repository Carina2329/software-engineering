package com.campus.contest.module.contest;

import com.campus.contest.TestInfrastructure;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.contest.dto.MilestoneCreateRequest;
import com.campus.contest.module.contest.dto.MilestoneUpdateRequest;
import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.entity.ContestMilestoneDO;
import com.campus.contest.module.contest.enums.EditionStatus;
import com.campus.contest.module.contest.mapper.ContestEditionMapper;
import com.campus.contest.module.contest.mapper.ContestMilestoneMapper;
import com.campus.contest.module.contest.service.ContestAuditService;
import com.campus.contest.module.contest.service.ContestAuthorizationService;
import com.campus.contest.module.contest.service.impl.MilestoneServiceImpl;
import com.campus.contest.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MilestoneServiceImplTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final ZoneOffset OFFSET = ZoneOffset.ofHours(8);

    @Mock
    private ContestEditionMapper editionMapper;
    @Mock
    private ContestMilestoneMapper milestoneMapper;
    @Mock
    private ContestAuthorizationService authorizationService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ContestAuditService auditService;

    private MilestoneServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MilestoneServiceImpl(
                editionMapper,
                milestoneMapper,
                authorizationService,
                TestInfrastructure.passthroughIdempotencyService(),
                currentUserProvider,
                auditService,
                TestInfrastructure.transactionTemplate()
        );
        lenient().when(currentUserProvider.requireUserId()).thenReturn(10L);
        lenient().when(authorizationService.canManage()).thenReturn(true);
    }

    @Test
    @DisplayName("赛程节点在核心时间范围内可以新增")
    void createsMilestoneWithinCoreWindow() {
        ContestEditionDO edition = edition(EditionStatus.REGISTERING);
        when(editionMapper.selectById(31L)).thenReturn(edition);
        when(milestoneMapper.insert(any(ContestMilestoneDO.class))).thenAnswer(invocation -> {
            ContestMilestoneDO entity = invocation.getArgument(0);
            entity.setId(51L);
            return 1;
        });
        LocalDateTime start = edition.getWorkDeadline().minusHours(4);

        MilestoneCreateRequest request = new MilestoneCreateRequest();
        request.setNodeType("WORK_END");
        request.setNodeName("作品提交答疑");
        request.setStartTime(start.atOffset(OFFSET));
        request.setEndTime(start.plusHours(1).atOffset(OFFSET));

        assertThat(service.create(31L, request, "milestone-key").id()).isEqualTo("51");
        verify(auditService).record(eq(10L), eq("EDITION_MILESTONE_CREATE"), any());
    }

    @Test
    @DisplayName("评审节点早于作品截止时间返回 400")
    void rejectsReviewMilestoneBeforeWorkDeadline() {
        ContestEditionDO edition = edition(EditionStatus.REGISTERING);
        when(editionMapper.selectById(31L)).thenReturn(edition);
        LocalDateTime start = edition.getWorkDeadline().minusHours(1);

        MilestoneCreateRequest request = new MilestoneCreateRequest();
        request.setNodeType("REVIEW");
        request.setNodeName("提前评审");
        request.setStartTime(start.atOffset(OFFSET));
        request.setEndTime(start.plusHours(1).atOffset(OFFSET));

        assertThatThrownBy(() -> service.create(31L, request, "invalid-milestone-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
        verify(milestoneMapper, never()).insert(any(ContestMilestoneDO.class));
    }

    @Test
    @DisplayName("归档届次不能新增赛程节点")
    void archivedEditionRejectsMilestoneChanges() {
        when(editionMapper.selectById(31L)).thenReturn(edition(EditionStatus.ARCHIVED));

        MilestoneCreateRequest request = new MilestoneCreateRequest();
        request.setNodeType("PUBLISH");
        request.setNodeName("公示答疑");
        request.setStartTime(LocalDateTime.now(SHANGHAI).plusDays(1).atOffset(OFFSET));
        request.setEndTime(LocalDateTime.now(SHANGHAI).plusDays(1).plusHours(1).atOffset(OFFSET));

        assertThatThrownBy(() -> service.create(31L, request, "archived-milestone-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode())
                                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION.getCode()));
    }

    @Test
    @DisplayName("EP-020 返回节点版本，使用该版本修改赛程节点成功")
    void queriedMilestoneVersionCanUpdateNode() {
        ContestEditionDO edition = edition(EditionStatus.REGISTERING);
        ContestMilestoneDO milestone = new ContestMilestoneDO();
        milestone.setId(51L);
        milestone.setEditionId(31L);
        milestone.setNodeType("WORK_END");
        milestone.setNodeName("作品截止");
        milestone.setStartTime(edition.getWorkDeadline().minusHours(2));
        milestone.setEndTime(edition.getWorkDeadline().minusHours(1));
        milestone.setEnabled(1);
        milestone.setVersion(4);
        when(editionMapper.selectById(31L)).thenReturn(edition);
        when(milestoneMapper.selectList(any())).thenReturn(List.of(milestone));
        when(milestoneMapper.selectById(51L)).thenReturn(milestone);
        when(milestoneMapper.updateById(any(ContestMilestoneDO.class))).thenReturn(1);

        int version = service.list(31L).get(0).version();
        MilestoneUpdateRequest request = new MilestoneUpdateRequest();
        request.setNodeName("作品提交结束");
        request.setVersion(version);

        assertThat(service.update(51L, request, "milestone-version-update-key").version())
                .isEqualTo(5);
    }

    @Test
    @DisplayName("赛程节点使用旧版本修改返回 409")
    void staleMilestoneVersionIsRejected() {
        ContestMilestoneDO milestone = new ContestMilestoneDO();
        milestone.setId(51L);
        milestone.setEditionId(31L);
        milestone.setNodeType("WORK_END");
        milestone.setNodeName("作品截止");
        milestone.setVersion(4);
        when(milestoneMapper.selectById(51L)).thenReturn(milestone);

        MilestoneUpdateRequest request = new MilestoneUpdateRequest();
        request.setNodeName("旧版本修改");
        request.setVersion(3);

        assertThatThrownBy(() -> service.update(51L, request, "stale-milestone-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode()));
        verify(milestoneMapper, never()).updateById(any(ContestMilestoneDO.class));
    }

    private ContestEditionDO edition(EditionStatus status) {
        LocalDateTime base = LocalDateTime.now(SHANGHAI).plusDays(1);
        ContestEditionDO edition = new ContestEditionDO();
        edition.setId(31L);
        edition.setStatus(status.getDatabaseValue());
        edition.setRegisterDeadline(base);
        edition.setWorkDeadline(base.plusDays(2));
        edition.setReviewStart(base.plusDays(3));
        edition.setPublishStart(base.plusDays(4));
        edition.setPublishEnd(base.plusDays(5));
        return edition;
    }
}
