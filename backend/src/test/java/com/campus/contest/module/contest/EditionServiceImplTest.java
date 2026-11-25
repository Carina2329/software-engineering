package com.campus.contest.module.contest;

import com.campus.contest.TestInfrastructure;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyScope;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.contest.dto.EditionCreateRequest;
import com.campus.contest.module.contest.dto.EditionPublishRequest;
import com.campus.contest.module.contest.dto.EditionSchoolQuotaRequest;
import com.campus.contest.module.contest.dto.EditionUpdateRequest;
import com.campus.contest.module.contest.entity.ContestDO;
import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.entity.EditionSchoolQuotaDO;
import com.campus.contest.module.contest.enums.EditionStatus;
import com.campus.contest.module.contest.mapper.ContestAccessCriteria;
import com.campus.contest.module.contest.mapper.ContestAccessMapper;
import com.campus.contest.module.contest.mapper.ContestEditionMapper;
import com.campus.contest.module.contest.mapper.ContestMapper;
import com.campus.contest.module.contest.mapper.ContestTeamMapper;
import com.campus.contest.module.contest.mapper.EditionSchoolQuotaMapper;
import com.campus.contest.module.contest.service.ContestAuditService;
import com.campus.contest.module.contest.service.ContestAuthorizationService;
import com.campus.contest.module.contest.service.impl.EditionServiceImpl;
import com.campus.contest.module.contest.vo.EditionCreateVO;
import com.campus.contest.module.contest.vo.EditionDetailVO;
import com.campus.contest.module.contest.vo.EditionPublishVO;
import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.organization.model.EffectiveScope;
import com.campus.contest.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Set;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EditionServiceImplTest {

    private static final Long OPERATOR_ID = 9L;
    private static final Long SCHOOL_ID = 10L;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final ZoneOffset OFFSET = ZoneOffset.ofHours(8);

    @Mock
    private ContestMapper contestMapper;
    @Mock
    private ContestEditionMapper editionMapper;
    @Mock
    private ContestAccessMapper accessMapper;
    @Mock
    private ContestTeamMapper teamMapper;
    @Mock
    private EditionSchoolQuotaMapper schoolQuotaMapper;
    @Mock
    private OrganizationMapper organizationMapper;
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
                accessMapper,
                teamMapper,
                schoolQuotaMapper,
                organizationMapper,
                authorizationService,
                TestInfrastructure.passthroughIdempotencyService(),
                currentUserProvider,
                auditService,
                TestInfrastructure.transactionTemplate()
        );
        lenient().when(currentUserProvider.requireUserId()).thenReturn(OPERATOR_ID);
        lenient().when(contestMapper.selectById(1L)).thenReturn(new ContestDO());
        lenient().when(authorizationService.currentScope())
                .thenReturn(new EffectiveScope(true, Set.of(), Set.of(), Set.of()));
        lenient().when(authorizationService.currentAccessCriteria())
                .thenReturn(accessCriteria());
        lenient().when(authorizationService.requireActiveOrganization(SCHOOL_ID))
                .thenReturn(schoolOrganization());
        lenient().when(organizationMapper.selectById(SCHOOL_ID))
                .thenReturn(schoolOrganization());
    }

    @Test
    @DisplayName("创建校级草稿并保存学校范围、可见范围和跨校组队")
    void createSchoolDraftWithExplicitScope() {
        when(editionMapper.selectCount(any())).thenReturn(0L);
        when(editionMapper.insert(any(ContestEditionDO.class))).thenAnswer(invocation -> {
            ContestEditionDO entity = invocation.getArgument(0);
            entity.setId(21L);
            return 1;
        });

        EditionCreateVO result = service.create(
                1L, validCreateRequest("2026 年校赛"), "edition-create-key"
        );

        assertThat(result.status()).isEqualTo("DRAFT");
        ArgumentCaptor<ContestEditionDO> captor =
                ArgumentCaptor.forClass(ContestEditionDO.class);
        verify(editionMapper).insert(captor.capture());
        assertThat(captor.getValue().getOwnerOrgId()).isEqualTo(SCHOOL_ID);
        assertThat(captor.getValue().getLevel()).isEqualTo("SCHOOL");
        assertThat(captor.getValue().getSchoolId()).isEqualTo(SCHOOL_ID);
        assertThat(captor.getValue().getVisibility()).isEqualTo("SCHOOL");
        assertThat(captor.getValue().getEntryMode()).isEqualTo("OPEN");
        assertThat(captor.getValue().getCrossSchoolTeamAllowed()).isEqualTo(1);
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
    @DisplayName("同一主办组织、同一年度下重名返回 409")
    void duplicateOwnerYearNameRejected() {
        when(editionMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() ->
                service.create(1L, validCreateRequest("2026 年校赛"), "dup-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.DUPLICATE_RELATION.getCode()));
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
    @DisplayName("校级比赛主办组织必须等于所属学校")
    void schoolContestRequiresOwnerSchool() {
        EditionCreateRequest request = validCreateRequest("主办范围错误");
        request.setOwnerOrgId("11");
        OrganizationDO another = schoolOrganization();
        another.setId(11L);
        when(authorizationService.requireActiveOrganization(11L)).thenReturn(another);

        assertThatThrownBy(() -> service.create(1L, request, "school-owner-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
    }

    @Test
    @DisplayName("同一幂等键但创建配置不同返回幂等冲突")
    void sameIdempotencyKeyWithDifferentConfigurationConflicts() {
        EditionServiceImpl strictService = new EditionServiceImpl(
                contestMapper,
                editionMapper,
                accessMapper,
                teamMapper,
                schoolQuotaMapper,
                organizationMapper,
                authorizationService,
                new StrictIdempotencyService(),
                currentUserProvider,
                auditService,
                TestInfrastructure.transactionTemplate()
        );
        when(editionMapper.selectCount(any())).thenReturn(0L);
        when(editionMapper.insert(any(ContestEditionDO.class))).thenAnswer(invocation -> {
            ContestEditionDO entity = invocation.getArgument(0);
            entity.setId(23L);
            return 1;
        });

        EditionCreateRequest first = validCreateRequest("幂等创建届次");
        assertThat(strictService.create(1L, first, "same-key").id()).isEqualTo("23");

        EditionCreateRequest changed = validCreateRequest("幂等创建届次");
        changed.setMaxTeamsPerTeacher(4);

        assertThatThrownBy(() -> strictService.create(1L, changed, "same-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode())
                                .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT.getCode()));
        verify(editionMapper, times(1)).insert(any(ContestEditionDO.class));
    }

    @Test
    @DisplayName("配置完整的校赛草稿发布为 REGISTERING，并默认使用 SCHOOL 可见")
    void publishesCompleteDraftWithoutTopicRows() {
        ContestEditionDO draft = validEdition(31L, EditionStatus.DRAFT);
        when(editionMapper.selectById(31L)).thenReturn(draft);
        when(editionMapper.updateById(any(ContestEditionDO.class))).thenReturn(1);
        EditionPublishRequest request = new EditionPublishRequest();
        request.setVersion(0);

        EditionPublishVO result = service.publish(31L, request, "publish-key");

        assertThat(result.status()).isEqualTo("REGISTERING");
        ArgumentCaptor<ContestEditionDO> captor =
                ArgumentCaptor.forClass(ContestEditionDO.class);
        verify(editionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getVisibility()).isEqualTo("SCHOOL");
        verify(authorizationService).requireContestEventManageable(any(ContestEditionDO.class));
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
    @DisplayName("报名截止已过不能发布")
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
        when(accessMapper.selectEditionById(any())).thenReturn(edition);
        when(organizationMapper.selectById(SCHOOL_ID)).thenReturn(schoolOrganization());

        EditionDetailVO detail = service.get(31L);

        assertThat(detail.status()).isEqualTo("REGISTERING");
        assertThat(detail.registrationOpen()).isTrue();
        assertThat(detail.submissionOpen()).isTrue();
        assertThat(detail.readOnly()).isFalse();
        assertThat(detail.ownerOrgName()).isEqualTo("测试学校");
        assertThat(detail.registerDeadline().getOffset()).isEqualTo(OFFSET);
    }

    @Test
    @DisplayName("访问 Mapper 不可见时草稿返回 404")
    void inaccessibleDraftIsHidden() {
        when(accessMapper.selectEditionById(any())).thenReturn(null);

        assertThatThrownBy(() -> service.get(31L))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("EP-015 竞赛不存在和不可见统一返回相同 404 文案")
    void editionPageHidesContestExistence() {
        when(accessMapper.selectContestById(any())).thenReturn(null);

        assertThatThrownBy(() -> service.page(99L, new com.campus.contest.module.contest.dto.EditionPageQuery()))
                .isInstanceOfSatisfying(BizException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode());
                    assertThat(error.getMessage()).isEqualTo("竞赛不存在或对当前用户不可见");
                });
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

        assertThatThrownBy(() -> service.update(31L, request, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode()));
        verify(editionMapper, never()).updateById(any(ContestEditionDO.class));
    }

    @Test
    @DisplayName("已发布届次截止时间不能缩短")
    void publishedDeadlineCannotBeShortened() {
        ContestEditionDO edition = validEdition(31L, EditionStatus.REGISTERING);
        when(editionMapper.selectById(31L)).thenReturn(edition);
        when(teamMapper.countByEditionId(31L)).thenReturn(0L);

        EditionUpdateRequest request = new EditionUpdateRequest();
        request.setVersion(0);
        request.setRegisterDeadline(edition.getRegisterDeadline().minusHours(1).atOffset(OFFSET));

        assertThatThrownBy(() -> service.update(31L, request, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode())
                                .isEqualTo(ErrorCode.BUSINESS_DEADLINE_PASSED.getCode()));
    }

    @Test
    @DisplayName("publish_end 已过去的 DRAFT 仍可修改")
    void expiredDraftRemainsEditable() {
        ContestEditionDO edition = validEdition(31L, EditionStatus.DRAFT);
        LocalDateTime past = LocalDateTime.now(SHANGHAI).minusDays(6);
        edition.setRegisterDeadline(past);
        edition.setWorkDeadline(past.plusDays(1));
        edition.setReviewStart(past.plusDays(2));
        edition.setPublishStart(past.plusDays(3));
        edition.setPublishEnd(past.plusDays(4));
        when(editionMapper.selectById(31L)).thenReturn(edition);
        when(teamMapper.countByEditionId(31L)).thenReturn(0L);
        when(editionMapper.updateById(any(ContestEditionDO.class))).thenReturn(1);

        EditionUpdateRequest request = new EditionUpdateRequest();
        request.setVersion(0);
        request.setEditionName("过期草稿仍可修改");

        assertThat(service.update(31L, request, null).version()).isEqualTo(1);
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

        assertThatThrownBy(() -> service.update(31L, request, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode()));
    }

    @Test
    @DisplayName("EP-018 在届次事务内完整替换学校名额并保留已占用数量")
    void replacesSchoolQuotasAndPreservesUsedTeams() {
        ContestEditionDO edition = validEdition(31L, EditionStatus.REGISTERING);
        when(editionMapper.selectById(31L)).thenReturn(edition);
        when(teamMapper.countByEditionId(31L)).thenReturn(0L);

        EditionSchoolQuotaDO existingSchool = schoolQuota(1L, SCHOOL_ID, 2, 1);
        EditionSchoolQuotaDO removedSchool = schoolQuota(2L, 30L, 3, 0);
        when(schoolQuotaMapper.selectByEditionForUpdate(31L))
                .thenReturn(List.of(existingSchool, removedSchool));
        when(schoolQuotaMapper.updateMaxTeams(eq(1L), eq(5), eq(OPERATOR_ID), any()))
                .thenReturn(1);
        when(schoolQuotaMapper.insert(any(EditionSchoolQuotaDO.class))).thenReturn(1);
        when(schoolQuotaMapper.softDeleteIfUnused(eq(2L), eq(OPERATOR_ID), any()))
                .thenReturn(1);
        when(editionMapper.updateById(any(ContestEditionDO.class))).thenReturn(1);

        OrganizationDO secondSchool = schoolOrganization();
        secondSchool.setId(20L);
        when(organizationMapper.selectById(20L)).thenReturn(secondSchool);

        EditionUpdateRequest request = new EditionUpdateRequest();
        request.setVersion(0);
        request.setSchoolQuotas(List.of(
                quota(String.valueOf(SCHOOL_ID), 5),
                quota("20", null)
        ));

        assertThat(service.update(31L, request, null).version()).isEqualTo(1);

        ArgumentCaptor<EditionSchoolQuotaDO> insertCaptor =
                ArgumentCaptor.forClass(EditionSchoolQuotaDO.class);
        verify(schoolQuotaMapper).insert(insertCaptor.capture());
        assertThat(insertCaptor.getValue().getSchoolId()).isEqualTo(20L);
        assertThat(insertCaptor.getValue().getMaxTeams()).isNull();
        assertThat(insertCaptor.getValue().getUsedTeams()).isZero();
        verify(schoolQuotaMapper).updateMaxTeams(eq(1L), eq(5), eq(OPERATOR_ID), any());
        verify(schoolQuotaMapper).softDeleteIfUnused(eq(2L), eq(OPERATOR_ID), any());
        verify(editionMapper).updateById(any(ContestEditionDO.class));
        verify(auditService).record(eq(OPERATOR_ID), eq("EDITION_UPDATE"), any());
    }

    @Test
    @DisplayName("学校名额不能降低到已占用队伍数以下")
    void schoolQuotaCannotBeReducedBelowUsedTeams() {
        ContestEditionDO edition = validEdition(31L, EditionStatus.REGISTERING);
        when(editionMapper.selectById(31L)).thenReturn(edition);
        when(teamMapper.countByEditionId(31L)).thenReturn(0L);
        when(schoolQuotaMapper.selectByEditionForUpdate(31L))
                .thenReturn(List.of(schoolQuota(1L, SCHOOL_ID, 5, 2)));

        EditionUpdateRequest request = new EditionUpdateRequest();
        request.setVersion(0);
        request.setSchoolQuotas(List.of(quota(String.valueOf(SCHOOL_ID), 1)));

        assertThatThrownBy(() -> service.update(31L, request, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode()));
        verify(schoolQuotaMapper, never()).updateMaxTeams(any(), any(), any(), any());
        verify(editionMapper, never()).updateById(any(ContestEditionDO.class));
    }

    @Test
    @DisplayName("已占用队伍的学校名额不能通过空数组删除")
    void cannotRemoveHeldSchoolQuota() {
        ContestEditionDO edition = validEdition(31L, EditionStatus.REGISTERING);
        when(editionMapper.selectById(31L)).thenReturn(edition);
        when(teamMapper.countByEditionId(31L)).thenReturn(0L);
        when(schoolQuotaMapper.selectByEditionForUpdate(31L))
                .thenReturn(List.of(schoolQuota(1L, SCHOOL_ID, 5, 1)));

        EditionUpdateRequest request = new EditionUpdateRequest();
        request.setVersion(0);
        request.setSchoolQuotas(List.of());

        assertThatThrownBy(() -> service.update(31L, request, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode()));
        verify(schoolQuotaMapper, never()).softDeleteIfUnused(any(), any(), any());
        verify(editionMapper, never()).updateById(any(ContestEditionDO.class));
    }

    @Test
    @DisplayName("空 school_quotas 清除所有未占用配置并恢复默认不限量")
    void emptySchoolQuotasClearUnusedConfigurations() {
        ContestEditionDO edition = validEdition(31L, EditionStatus.REGISTERING);
        when(editionMapper.selectById(31L)).thenReturn(edition);
        when(teamMapper.countByEditionId(31L)).thenReturn(0L);
        when(schoolQuotaMapper.selectByEditionForUpdate(31L))
                .thenReturn(List.of(schoolQuota(1L, SCHOOL_ID, 5, 0)));
        when(schoolQuotaMapper.softDeleteIfUnused(eq(1L), eq(OPERATOR_ID), any()))
                .thenReturn(1);
        when(editionMapper.updateById(any(ContestEditionDO.class))).thenReturn(1);

        EditionUpdateRequest request = new EditionUpdateRequest();
        request.setVersion(0);
        request.setSchoolQuotas(List.of());

        assertThat(service.update(31L, request, null).version()).isEqualTo(1);
        verify(schoolQuotaMapper).softDeleteIfUnused(eq(1L), eq(OPERATOR_ID), any());
        verify(editionMapper).updateById(any(ContestEditionDO.class));
    }

    @Test
    @DisplayName("school_quotas 中同一学校不能重复")
    void schoolQuotaSchoolMustBeUnique() {
        ContestEditionDO edition = validEdition(31L, EditionStatus.REGISTERING);
        when(editionMapper.selectById(31L)).thenReturn(edition);
        when(teamMapper.countByEditionId(31L)).thenReturn(0L);

        EditionUpdateRequest request = new EditionUpdateRequest();
        request.setVersion(0);
        request.setSchoolQuotas(List.of(
                quota(String.valueOf(SCHOOL_ID), 2),
                quota(String.valueOf(SCHOOL_ID), 3)
        ));

        assertThatThrownBy(() -> service.update(31L, request, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
        verify(schoolQuotaMapper, never()).selectByEditionForUpdate(any());
        verify(editionMapper, never()).updateById(any(ContestEditionDO.class));
    }

    @Test
    @DisplayName("school_quotas 必须对应启用状态的学校组织")
    void schoolQuotaRequiresActiveSchool() {
        ContestEditionDO edition = validEdition(31L, EditionStatus.REGISTERING);
        when(editionMapper.selectById(31L)).thenReturn(edition);
        when(teamMapper.countByEditionId(31L)).thenReturn(0L);
        OrganizationDO disabled = schoolOrganization();
        disabled.setStatus(0);
        when(organizationMapper.selectById(SCHOOL_ID)).thenReturn(disabled);

        EditionUpdateRequest request = new EditionUpdateRequest();
        request.setVersion(0);
        request.setSchoolQuotas(List.of(quota(String.valueOf(SCHOOL_ID), 2)));

        assertThatThrownBy(() -> service.update(31L, request, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
        verify(schoolQuotaMapper, never()).selectByEditionForUpdate(any());
        verify(editionMapper, never()).updateById(any(ContestEditionDO.class));
    }

    private ContestAccessCriteria accessCriteria() {
        ContestAccessCriteria criteria = new ContestAccessCriteria();
        criteria.setManager(false);
        criteria.setViewerSchoolId(SCHOOL_ID);
        criteria.setViewerProvinceCode(510000L);
        return criteria;
    }

    private EditionCreateRequest validCreateRequest(String name) {
        LocalDateTime base = LocalDateTime.now(SHANGHAI).plusDays(1);
        EditionCreateRequest request = new EditionCreateRequest();
        request.setEditionName(name);
        request.setYear(2026);
        request.setOwnerOrgId(String.valueOf(SCHOOL_ID));
        request.setLevel("SCHOOL");
        request.setSchoolId(String.valueOf(SCHOOL_ID));
        request.setVisibility("SCHOOL");
        request.setEntryMode("OPEN");
        request.setCrossSchoolTeamAllowed(true);
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
        edition.setOwnerOrgId(SCHOOL_ID);
        edition.setEditionName("2026 年校赛");
        edition.setYear(2026);
        edition.setLevel("SCHOOL");
        edition.setSchoolId(SCHOOL_ID);
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
        edition.setVisibility(status == EditionStatus.DRAFT ? "DRAFT" : "SCHOOL");
        edition.setEntryMode("OPEN");
        edition.setCrossSchoolTeamAllowed(1);
        edition.setVersion(0);
        return edition;
    }

    private OrganizationDO schoolOrganization() {
        OrganizationDO organization = new OrganizationDO();
        organization.setId(SCHOOL_ID);
        organization.setOrgType("SCHOOL");
        organization.setOrgName("测试学校");
        organization.setProvinceCode("510000");
        organization.setStatus(1);
        return organization;
    }

    private EditionSchoolQuotaRequest quota(String schoolId, Integer maxTeams) {
        EditionSchoolQuotaRequest request = new EditionSchoolQuotaRequest();
        request.setSchoolId(schoolId);
        request.setMaxTeams(maxTeams);
        return request;
    }

    private EditionSchoolQuotaDO schoolQuota(
            Long id,
            Long schoolId,
            Integer maxTeams,
            Integer usedTeams
    ) {
        EditionSchoolQuotaDO quota = new EditionSchoolQuotaDO();
        quota.setId(id);
        quota.setEditionId(31L);
        quota.setSchoolId(schoolId);
        quota.setMaxTeams(maxTeams);
        quota.setUsedTeams(usedTeams);
        quota.setDeleted(0);
        quota.setVersion(0);
        return quota;
    }

    private static final class StrictIdempotencyService implements IdempotencyService {

        private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        private final Map<String, String> fingerprints = new HashMap<>();
        private final Map<String, Object> results = new HashMap<>();

        @Override
        public <T> T execute(
                IdempotencyScope scope,
                String operation,
                String idempotencyKey,
                Object request,
                java.time.Duration ttl,
                Class<T> resultType,
                Supplier<T> action
        ) {
            String key = scope.principalId() + ":" + operation + ":" + idempotencyKey;
            String fingerprint;
            try {
                fingerprint = objectMapper.writeValueAsString(request);
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                throw new BizException(ErrorCode.INVALID_PARAM.getCode(), "测试请求无法序列化");
            }
            String existing = fingerprints.putIfAbsent(key, fingerprint);
            if (existing != null) {
                if (!existing.equals(fingerprint)) {
                    throw new BizException(
                            ErrorCode.IDEMPOTENCY_CONFLICT.getCode(),
                            ErrorCode.IDEMPOTENCY_CONFLICT.getMessage()
                    );
                }
                return resultType.cast(results.get(key));
            }
            T result = action.get();
            results.put(key, result);
            return result;
        }
    }
}
