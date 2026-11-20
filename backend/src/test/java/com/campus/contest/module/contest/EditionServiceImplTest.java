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
import com.campus.contest.module.contest.mapper.ContestAccessCriteria;
import com.campus.contest.module.contest.mapper.ContestAccessMapper;
import com.campus.contest.module.contest.mapper.ContestEditionMapper;
import com.campus.contest.module.contest.mapper.ContestMapper;
import com.campus.contest.module.contest.mapper.ContestTeamMapper;
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
        verify(authorizationService).requireEventManageable(31L);
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
    @DisplayName("DRAFT 届次可以提前核心时间")
    void draftDeadlineCanBeChanged() {
        ContestEditionDO edition = validEdition(31L, EditionStatus.DRAFT);
        when(editionMapper.selectById(31L)).thenReturn(edition);
        when(teamMapper.countByEditionId(31L)).thenReturn(0L);
        when(editionMapper.updateById(any(ContestEditionDO.class))).thenReturn(1);

        EditionUpdateRequest request = new EditionUpdateRequest();
        request.setVersion(0);
        request.setRegisterDeadline(edition.getRegisterDeadline().minusHours(1).atOffset(OFFSET));

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
}
