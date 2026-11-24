package com.campus.contest.module.team.support;

import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.organization.model.EffectiveScope;
import com.campus.contest.module.organization.service.OrganizationScopeService;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper.EditionRow;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper.GrantRow;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper.MemberRow;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper.QuotaRow;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper.RegistrationRow;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper.TeamRow;
import com.campus.contest.module.team.support.model.RegistrationCheckMode;
import com.campus.contest.module.team.support.model.RegistrationEligibilityContext;
import com.campus.contest.module.team.support.model.RegistrationEligibilityResult;
import com.campus.contest.module.team.support.model.RegistrationMemberSnapshot;
import com.campus.contest.module.team.support.service.impl.RegistrationEligibilityServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegistrationEligibilityServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");

    private RegistrationSupportMapper supportMapper;
    private SysUserMapper userMapper;
    private OrganizationMapper organizationMapper;
    private OrganizationScopeService scopeService;
    private ObjectMapper objectMapper;
    private RegistrationEligibilityServiceImpl service;

    @BeforeEach
    void setUp() {
        supportMapper = mock(RegistrationSupportMapper.class);
        userMapper = mock(SysUserMapper.class);
        organizationMapper = mock(OrganizationMapper.class);
        scopeService = mock(OrganizationScopeService.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new RegistrationEligibilityServiceImpl(
                supportMapper,
                userMapper,
                organizationMapper,
                scopeService,
                objectMapper,
                Clock.fixed(NOW, ZONE)
        );
    }

    @Test
    void openTeamCanSubmitWhenProfileMembersAndDeadlineAreValid() {
        prepareBase(true, 0, edition("PUBLIC", "OPEN", true, 0));
        when(supportMapper.countLiveRegistration(10L, 20L)).thenReturn(0L);

        RegistrationEligibilityResult result = service.checkCanRegister(context(
                RegistrationCheckMode.SUBMIT,
                null
        ));

        assertThat(result.canRegister()).isTrue();
        assertThat(result.registerState()).isEqualTo("OPEN");
        assertThat(result.quotaSchoolId()).isEqualTo(2L);
    }

    @Test
    void submitRejectsExpiredDeadlineAndDuplicateLiveRegistration() {
        prepareBase(true, 0, edition("PUBLIC", "OPEN", false, 0));
        assertThat(service.checkCanRegister(context(RegistrationCheckMode.SUBMIT, null))
                .registerState()).isEqualTo("DEADLINE_PASSED");

        prepareBase(true, 0, edition("PUBLIC", "OPEN", true, 0));
        when(supportMapper.countLiveRegistration(10L, 20L)).thenReturn(1L);
        assertThat(service.checkCanRegister(context(RegistrationCheckMode.SUBMIT, null))
                .registerState()).isEqualTo("ALREADY_REGISTERED");
    }

    @Test
    void invisibleInvitedAndQualifiedModesReturnSpecificStates() {
        prepareBase(true, 0, edition("SCHOOL", "OPEN", true, 0));
        when(supportMapper.selectEdition(20L)).thenReturn(new EditionRow(
                20L, 1, "SCHOOL", 99L, null, "OPEN",
                LocalDateTime.now(Clock.fixed(NOW, ZONE)).plusDays(1),
                1, 5, 0
        ));
        assertThat(service.checkCanRegister(context(RegistrationCheckMode.SUBMIT, null))
                .registerState()).isEqualTo("NOT_VISIBLE");

        prepareBase(true, 0, edition("PUBLIC", "INVITED", true, 0));
        assertThat(service.checkCanRegister(context(RegistrationCheckMode.SUBMIT, null))
                .registerState()).isEqualTo("NOT_INVITED");

        prepareBase(true, 0, edition("PUBLIC", "QUALIFIED", true, 0));
        assertThat(service.checkCanRegister(context(RegistrationCheckMode.SUBMIT, null))
                .registerState()).isEqualTo("NOT_QUALIFIED");

        when(supportMapper.selectActiveGrant(20L, 10L, 30L, 2L, "20260001"))
                .thenReturn(new GrantRow(7L, "QUALIFIED", 10L));
        RegistrationEligibilityResult qualified =
                service.checkCanRegister(context(RegistrationCheckMode.SUBMIT, null));
        assertThat(qualified.canRegister()).isTrue();
        assertThat(qualified.registerState()).isEqualTo("QUALIFIED_ONLY");
        assertThat(qualified.entryGrantId()).isEqualTo(7L);
    }

    @Test
    void quotaAndMemberRulesAreEnforced() {
        prepareBase(true, 0, edition("PUBLIC", "OPEN", true, 0));
        when(supportMapper.selectQuota(20L, 2L)).thenReturn(new QuotaRow(3L, 1, 1));
        RegistrationEligibilityResult full =
                service.checkCanRegister(context(RegistrationCheckMode.SUBMIT, null));
        assertThat(full.registerState()).isEqualTo("SCHOOL_QUOTA_FULL");

        prepareBase(true, 0, edition("PUBLIC", "OPEN", true, 0));
        MemberRow outsider = new MemberRow(
                12L, null, 20L, "Other School Member", 3L, "20260002", 0, 1
        );
        when(supportMapper.selectActiveMembers(10L)).thenReturn(List.of(leaderMember(), outsider));
        when(organizationMapper.selectById(3L)).thenReturn(school(3L, true));
        RegistrationEligibilityResult crossSchool =
                service.checkCanRegister(context(RegistrationCheckMode.SUBMIT, null));
        assertThat(crossSchool.registerState()).isEqualTo("MEMBERS_INVALID");
    }

    @Test
    void adminReviewSkipsDeadlineButRejectsChangedSnapshot() throws Exception {
        prepareBase(true, 2, edition("PUBLIC", "OPEN", false, 0));
        String snapshot = objectMapper.writeValueAsString(List.of(new RegistrationMemberSnapshot(
                11L, 30L, "Leader User", 2L, "20260001", true
        )));
        when(supportMapper.selectRegistration(50L)).thenReturn(new RegistrationRow(
                50L, 10L, 20L, "PENDING_ADMIN", snapshot, 0, 2L
        ));

        RegistrationEligibilityResult valid = service.checkCanRegister(context(
                RegistrationCheckMode.ADMIN_REVIEW,
                50L
        ));
        assertThat(valid.canRegister()).isTrue();

        when(supportMapper.selectActiveMembers(10L)).thenReturn(List.of(
                new MemberRow(11L, 30L, 20L, "Changed Name", 2L, "20260001", 1, 1)
        ));
        RegistrationEligibilityResult changed = service.checkCanRegister(context(
                RegistrationCheckMode.ADMIN_REVIEW,
                50L
        ));
        assertThat(changed.registerState()).isEqualTo("MEMBERS_INVALID");
    }

    @Test
    void archivedEditionCannotBeApprovedAfterDeadline() {
        EditionRow archived = edition("PUBLIC", "OPEN", false, 0);
        archived = new EditionRow(
                archived.id(), 5, archived.visibility(), archived.schoolId(),
                archived.provinceCode(), archived.entryMode(), archived.registerDeadline(),
                archived.minTeamSize(), archived.maxTeamSize(), archived.crossSchoolTeamAllowed()
        );
        prepareBase(true, 2, archived);
        when(supportMapper.selectRegistration(50L)).thenReturn(new RegistrationRow(
                50L, 10L, 20L, "PENDING_ADMIN", "[]", 0, 2L
        ));

        RegistrationEligibilityResult result = service.checkCanRegister(context(
                RegistrationCheckMode.ADMIN_REVIEW,
                50L
        ));

        assertThat(result.registerState()).isEqualTo("REGISTRATION_STATE_INVALID");
        assertThat(result.reason()).contains("已归档");
    }

    @Test
    void adminReviewRequiresLimitedQuotaToStillBeHeldByCurrentRound() throws Exception {
        prepareBase(true, 2, edition("PUBLIC", "OPEN", true, 0));
        String snapshot = objectMapper.writeValueAsString(List.of(new RegistrationMemberSnapshot(
                11L, 30L, "Leader User", 2L, "20260001", true
        )));
        when(supportMapper.selectRegistration(50L)).thenReturn(new RegistrationRow(
                50L, 10L, 20L, "PENDING_ADMIN", snapshot, 0, 2L
        ));
        when(supportMapper.selectQuota(20L, 2L)).thenReturn(new QuotaRow(3L, 5, 0));

        RegistrationEligibilityResult result = service.checkCanRegister(context(
                RegistrationCheckMode.ADMIN_REVIEW,
                50L
        ));

        assertThat(result.canRegister()).isFalse();
        assertThat(result.registerState()).isEqualTo("REGISTRATION_STATE_INVALID");
        assertThat(result.reason()).contains("名额");
    }

    private void prepareBase(boolean futureDeadline, int teamStatus, EditionRow edition) {
        when(supportMapper.selectTeam(10L)).thenReturn(new TeamRow(
                10L, 20L, 30L, teamStatus, null
        ));
        when(supportMapper.selectEdition(20L)).thenReturn(edition);
        SysUserDO leader = new SysUserDO();
        leader.setId(30L);
        leader.setStudentNo("20260001");
        leader.setSchoolId(2L);
        leader.setSchoolVerificationStatus("VERIFIED");
        leader.setStatus(1);
        leader.setDeleted(0);
        when(userMapper.selectById(30L)).thenReturn(leader);
        when(organizationMapper.selectById(2L)).thenReturn(school(2L, true));
        when(supportMapper.selectActiveMembers(10L)).thenReturn(List.of(leaderMember()));
        when(scopeService.loadRequired(30L)).thenReturn(new EffectiveScope(
                false, Set.of(), Set.of(), Set.of()
        ));
    }

    private EditionRow edition(
            String visibility,
            String entryMode,
            boolean futureDeadline,
            int crossSchool
    ) {
        LocalDateTime now = LocalDateTime.now(Clock.fixed(NOW, ZONE));
        return new EditionRow(
                20L,
                1,
                visibility,
                null,
                null,
                entryMode,
                futureDeadline ? now.plusDays(1) : now.minusMinutes(1),
                1,
                5,
                crossSchool
        );
    }

    private MemberRow leaderMember() {
        return new MemberRow(11L, 30L, 20L, "Leader User", 2L, "20260001", 1, 1);
    }

    private OrganizationDO school(Long id, boolean active) {
        OrganizationDO school = new OrganizationDO();
        school.setId(id);
        school.setOrgType("SCHOOL");
        school.setProvinceCode("510000");
        school.setStatus(active ? 1 : 0);
        school.setDeleted(0);
        return school;
    }

    private RegistrationEligibilityContext context(
            RegistrationCheckMode mode,
            Long registrationId
    ) {
        return new RegistrationEligibilityContext(10L, 20L, registrationId, mode);
    }
}
