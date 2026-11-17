package com.campus.contest.module.contest;

import com.campus.contest.TestInfrastructure;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.contest.dto.EditionCreateRequest;
import com.campus.contest.module.contest.dto.EditionPublishRequest;
import com.campus.contest.module.contest.dto.EditionUpdateRequest;
import com.campus.contest.module.contest.entity.ContestDO;
import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.enums.EditionStatus;
import com.campus.contest.module.contest.mapper.ContestEditionMapper;
import com.campus.contest.module.contest.mapper.ContestMapper;
import com.campus.contest.module.contest.mapper.ContestTeamMapper;
import com.campus.contest.module.contest.service.ContestAuditService;
import com.campus.contest.module.contest.service.ContestAuthorizationService;
import com.campus.contest.module.contest.service.impl.EditionServiceImpl;
import com.campus.contest.module.contest.vo.EditionCreateVO;
import com.campus.contest.module.contest.vo.EditionDetailVO;
import com.campus.contest.module.contest.vo.EditionPublishVO;
import com.campus.contest.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

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
class EditionServiceImplTest {

    private static final Long OPERATOR_ID = 9L;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final ZoneOffset OFFSET = ZoneOffset.ofHours(8);

    @Mock
    private ContestMapper contestMapper;
    @Mock
    private ContestEditionMapper editionMapper;
    @Mock
    private ContestTeamMapper teamMapper;
    @Mock
    private ContestAuthorizationService authorizationService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ContestAuditService auditService;

    private EditionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EditionServiceImpl(
                contestMapper,
                editionMapper,
                teamMapper,
                authorizationService,
                TestInfrastructure.passthroughIdempotencyService(),
                currentUserProvider,
                auditService,
                TestInfrastructure.transactionTemplate()
        );
        lenient().when(currentUserProvider.requireUserId()).thenReturn(OPERATOR_ID);
        lenient().when(contestMapper.selectById(1L)).thenReturn(new ContestDO());
    }

    @Test
    @DisplayName("创建届次固定为 DRAFT，固定人数正确落库")
    void createDraftWithFixedTeamSize() {
        when(editionMapper.selectCount(any())).thenReturn(0L);
        when(editionMapper.insert(any(ContestEditionDO.class))).thenAnswer(invocation -> {
            ContestEditionDO entity = invocation.getArgument(0);
            entity.setId(21L);
            return 1;
        });

        EditionCreateRequest request = validCreateRequest("2026 年校赛");
        request.setTeamSizeMode("FIXED");
        request.setMinTeamSize(4);
        request.setMaxTeamSize(4);

        EditionCreateVO result = service.create(1L, request, "edition-create-key");

        assertThat(result.id()).isEqualTo("21");
        assertThat(result.status()).isEqualTo("DRAFT");
        ArgumentCaptor<ContestEditionDO> captor =
                ArgumentCaptor.forClass(ContestEditionDO.class);
        verify(editionMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isZero();
        assertThat(captor.getValue().getTeamSizeMode()).isEqualTo("FIXED");
        assertThat(captor.getValue().getMinTeamSize()).isEqualTo(4);
        assertThat(captor.getValue().getMaxTeamSize()).isEqualTo(4);
    }

    @Test
    @DisplayName("范围人数允许不同最小值与最大值")
    void createRangeTeamSize() {
        when(editionMapper.selectCount(any())).thenReturn(0L);
        when(editionMapper.insert(any(ContestEditionDO.class))).thenAnswer(invocation -> {
            ContestEditionDO entity = invocation.getArgument(0);
            entity.setId(22L);
            return 1;
        });

        EditionCreateRequest request = validCreateRequest("2026 年范围赛");
        request.setTeamSizeMode("RANGE");
        request.setMinTeamSize(2);
        request.setMaxTeamSize(5);

        assertThat(service.create(1L, request, "range-key").status()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("固定人数最小值和最大值不同返回 400")
    void fixedTeamSizeRejectsDifferentSizes() {
        EditionCreateRequest request = validCreateRequest("非法固定人数");
        request.setTeamSizeMode("FIXED");
        request.setMinTeamSize(2);
        request.setMaxTeamSize(3);

        assertThatThrownBy(() -> service.create(1L, request, "fixed-invalid-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
        verify(editionMapper, never()).insert(any(ContestEditionDO.class));
    }

    @Test
    @DisplayName("同竞赛下重名届次返回 409")
    void duplicateEditionNameRejected() {
        when(editionMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(1L, validCreateRequest("2026 年校赛"), "dup-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.DUPLICATE_RELATION.getCode()));
        verify(editionMapper, never()).insert(any(ContestEditionDO.class));
    }

    @Test
    @DisplayName("同一年份不同届次名称可以创建")
    void sameYearDifferentEditionNamesAreAllowed() {
        when(editionMapper.selectCount(any())).thenReturn(0L);
        when(editionMapper.insert(any(ContestEditionDO.class))).thenAnswer(invocation -> {
            ContestEditionDO entity = invocation.getArgument(0);
            entity.setId(23L);
            return 1;
        });

        assertThat(service.create(1L, validCreateRequest("2026 年挑战赛"), "same-year-key").id())
                .isEqualTo("23");
    }

    @Test
    @DisplayName("教师指导上限超出 1-10 返回 400")
    void teacherLimitMustBeBetweenOneAndTen() {
        EditionCreateRequest request = validCreateRequest("教师上限非法");
        request.setMaxTeamsPerTeacher(11);

        assertThatThrownBy(() -> service.create(1L, request, "teacher-limit-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
    }

    @Test
    @DisplayName("FIRST_COME 只能用于限额统一赛题")
    void firstComeRequiresLimitedPresetMode() {
        EditionCreateRequest request = validCreateRequest("分配模式非法");
        request.setTopicMode("PRESET_UNLIMITED");
        request.setTopicAllocationMode("FIRST_COME");

        assertThatThrownBy(() -> service.create(1L, request, "allocation-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
    }

    @Test
    @DisplayName("配置完整的草稿发布为 REGISTERING，不要求赛题已存在")
    void publishesCompleteDraftWithoutTopicRows() {
        ContestEditionDO draft = validEdition(31L, EditionStatus.DRAFT);
        when(editionMapper.selectById(31L)).thenReturn(draft);
        when(editionMapper.updateById(any(ContestEditionDO.class))).thenReturn(1);
        EditionPublishRequest request = new EditionPublishRequest();
        request.setVersion(0);

        EditionPublishVO result = service.publish(31L, request, "publish-key");

        assertThat(result.status()).isEqualTo("REGISTERING");
        verify(editionMapper).updateById(any(ContestEditionDO.class));
        verify(auditService).record(eq(OPERATOR_ID), eq("EDITION_PUBLISH"), any());
    }

    @Test
    @DisplayName("核心时间缺失不能发布")
    void publishRejectsMissingCoreTime() {
        ContestEditionDO draft = validEdition(31L, EditionStatus.DRAFT);
        draft.setPublishEnd(null);
        when(editionMapper.selectById(31L)).thenReturn(draft);
        EditionPublishRequest request = new EditionPublishRequest();
        request.setVersion(0);

        assertThatThrownBy(() -> service.publish(31L, request, "publish-missing-time"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
        verify(editionMapper, never()).updateById(any(ContestEditionDO.class));
    }

    @Test
    @DisplayName("核心时间顺序错误不能发布")
    void publishRejectsInvalidTimeOrder() {
        ContestEditionDO draft = validEdition(31L, EditionStatus.DRAFT);
        draft.setWorkDeadline(draft.getRegisterDeadline().minusMinutes(1));
        when(editionMapper.selectById(31L)).thenReturn(draft);
        EditionPublishRequest request = new EditionPublishRequest();
        request.setVersion(0);

        assertThatThrownBy(() -> service.publish(31L, request, "publish-time-order"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
    }

    @Test
    @DisplayName("报名截止已过不能发布，返回业务截止时间错误")
    void publishRejectsPastRegistrationDeadline() {
        ContestEditionDO draft = validEdition(31L, EditionStatus.DRAFT);
        draft.setRegisterDeadline(LocalDateTime.now(SHANGHAI).minusMinutes(1));
        when(editionMapper.selectById(31L)).thenReturn(draft);
        EditionPublishRequest request = new EditionPublishRequest();
        request.setVersion(0);

        assertThatThrownBy(() -> service.publish(31L, request, "publish-past-deadline"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode())
                                .isEqualTo(ErrorCode.BUSINESS_DEADLINE_PASSED.getCode()));
    }

    @Test
    @DisplayName("普通用户查询已发布届次可见报名和作品窗口")
    void ordinaryUserSeesCurrentWindows() {
        ContestEditionDO edition = validEdition(31L, EditionStatus.REGISTERING);
        when(editionMapper.selectById(31L)).thenReturn(edition);
        when(authorizationService.canManage()).thenReturn(false);

        EditionDetailVO detail = service.get(31L);

        assertThat(detail.status()).isEqualTo("REGISTERING");
        assertThat(detail.registrationOpen()).isTrue();
        assertThat(detail.submissionOpen()).isTrue();
        assertThat(detail.readOnly()).isFalse();
        assertThat(detail.registerDeadline().getOffset()).isEqualTo(OFFSET);
    }

    @Test
    @DisplayName("普通用户无法读取草稿届次，返回 404")
    void draftEditionIsHiddenFromOrdinaryUser() {
        ContestEditionDO draft = validEdition(31L, EditionStatus.DRAFT);
        when(editionMapper.selectById(31L)).thenReturn(draft);
        when(authorizationService.canManage()).thenReturn(false);

        assertThatThrownBy(() -> service.get(31L))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("产生队伍后不能修改人数、赛题模式、更换次数或教师上限")
    void teamPresenceFreezesStructuralFields() {
        ContestEditionDO edition = validEdition(31L, EditionStatus.REGISTERING);
        when(editionMapper.selectById(31L)).thenReturn(edition);
        when(teamMapper.countByEditionId(31L)).thenReturn(1L);

        EditionUpdateRequest request = new EditionUpdateRequest();
        request.setVersion(0);
        request.setTopicMode("SELF_PROPOSED");

        assertThatThrownBy(() -> service.update(31L, request, "frozen-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode()));
        verify(editionMapper, never()).updateById(any(ContestEditionDO.class));
    }

    @Test
    @DisplayName("截止阶段开始后只能延长核心时间")
    void pastDeadlineCanOnlyBeExtended() {
        ContestEditionDO edition = validEdition(31L, EditionStatus.REGISTERING);
        LocalDateTime yesterday = LocalDateTime.now(SHANGHAI).minusDays(1);
        edition.setRegisterDeadline(yesterday);
        edition.setWorkDeadline(yesterday.plusDays(2));
        edition.setReviewStart(yesterday.plusDays(3));
        edition.setPublishStart(yesterday.plusDays(4));
        edition.setPublishEnd(yesterday.plusDays(5));
        when(editionMapper.selectById(31L)).thenReturn(edition);
        when(teamMapper.countByEditionId(31L)).thenReturn(0L);

        EditionUpdateRequest request = new EditionUpdateRequest();
        request.setVersion(0);
        request.setRegisterDeadline(yesterday.minusHours(1).atOffset(OFFSET));

        assertThatThrownBy(() -> service.update(31L, request, "shorten-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode())
                                .isEqualTo(ErrorCode.BUSINESS_DEADLINE_PASSED.getCode()));
    }

    @Test
    @DisplayName("届次更新版本落后返回 409")
    void editionUpdateRejectsStaleVersion() {
        ContestEditionDO edition = validEdition(31L, EditionStatus.REGISTERING);
        edition.setVersion(2);
        when(editionMapper.selectById(31L)).thenReturn(edition);

        EditionUpdateRequest request = new EditionUpdateRequest();
        request.setVersion(1);
        request.setEditionName("新名称");

        assertThatThrownBy(() -> service.update(31L, request, "stale-edition-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode()));
    }

    private EditionCreateRequest validCreateRequest(String name) {
        LocalDateTime base = LocalDateTime.now(SHANGHAI).plusDays(1);
        EditionCreateRequest request = new EditionCreateRequest();
        request.setEditionName(name);
        request.setYear(2026);
        request.setRegisterDeadline(base.atOffset(OFFSET));
        request.setWorkDeadline(base.plusDays(2).atOffset(OFFSET));
        request.setReviewStart(base.plusDays(3).atOffset(OFFSET));
        request.setPublishStart(base.plusDays(4).atOffset(OFFSET));
        request.setPublishEnd(base.plusDays(5).atOffset(OFFSET));
        request.setMinTeamSize(2);
        request.setMaxTeamSize(5);
        request.setTeamSizeMode("RANGE");
        request.setMaxTeamsPerTeacher(3);
        request.setTopicMode("PRESET_UNLIMITED");
        request.setTopicReviewRequired(true);
        request.setTopicAllocationMode("REVIEW");
        request.setTopicChangeLimit(1);
        return request;
    }

    private ContestEditionDO validEdition(Long id, EditionStatus status) {
        LocalDateTime base = LocalDateTime.now(SHANGHAI).plusDays(1);
        ContestEditionDO edition = new ContestEditionDO();
        edition.setId(id);
        edition.setContestId(1L);
        edition.setEditionName("2026 年校赛");
        edition.setYear(2026);
        edition.setStatus(status.getDatabaseValue());
        edition.setRegisterDeadline(base);
        edition.setWorkDeadline(base.plusDays(2));
        edition.setReviewStart(base.plusDays(3));
        edition.setPublishStart(base.plusDays(4));
        edition.setPublishEnd(base.plusDays(5));
        edition.setMinTeamSize(2);
        edition.setMaxTeamSize(5);
        edition.setTeamSizeMode("RANGE");
        edition.setMaxTeamsPerTeacher(3);
        edition.setTopicMode("PRESET_UNLIMITED");
        edition.setTopicReviewRequired(1);
        edition.setTopicAllocationMode("REVIEW");
        edition.setTopicChangeLimit(1);
        edition.setVersion(0);
        return edition;
    }
}
