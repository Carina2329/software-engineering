package com.campus.contest.module.team;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.enums.EditionStatus;
import com.campus.contest.module.contest.mapper.ContestEditionMapper;
import com.campus.contest.module.contest.service.ContestAuthorizationService;
import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.system.dto.AuthorizationSnapshot;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.system.service.AuthorizationSnapshotService;
import com.campus.contest.module.team.dto.EntryGrantBatchItem;
import com.campus.contest.module.team.dto.EntryGrantBatchRequest;
import com.campus.contest.module.team.entity.EditionEntryGrantDO;
import com.campus.contest.module.team.mapper.EditionEntryGrantMapper;
import com.campus.contest.module.team.service.TeamAuditService;
import com.campus.contest.module.team.service.impl.EditionEntryGrantServiceImpl;
import com.campus.contest.module.team.vo.EntryGrantBatchVO;
import com.campus.contest.module.team.vo.EntryGrantListVO;
import com.campus.contest.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EditionEntryGrantServiceImplTest {

    @Mock
    private EditionEntryGrantMapper entryGrantMapper;
    @Mock
    private ContestEditionMapper editionMapper;
    @Mock
    private ContestAuthorizationService contestAuthorizationService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private OrganizationMapper organizationMapper;
    @Mock
    private AuthorizationSnapshotService authorizationSnapshotService;
    @Mock
    private TeamAuditService auditService;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private EditionEntryGrantServiceImpl service;
    private ContestEditionDO edition;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-11-25T02:00:00Z"),
                ZoneId.of("Asia/Shanghai")
        );
        service = new EditionEntryGrantServiceImpl(
                entryGrantMapper,
                editionMapper,
                contestAuthorizationService,
                currentUserProvider,
                userMapper,
                organizationMapper,
                authorizationSnapshotService,
                auditService,
                idempotencyService,
                transactionTemplate,
                clock
        );
        edition = new ContestEditionDO();
        edition.setId(10L);
        edition.setStatus(EditionStatus.REGISTERING.getDatabaseValue());
        edition.setLevel("SCHOOL");
        edition.setOwnerOrgId(100L);
        lenient().when(editionMapper.selectById(10L)).thenReturn(edition);
        lenient().when(entryGrantMapper.selectEditionForUpdate(10L)).thenReturn(edition);
        lenient().when(currentUserProvider.requireUserId()).thenReturn(9L);
        lenient().when(organizationMapper.selectById(20L)).thenReturn(activeSchool());
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        lenient().when(idempotencyService.execute(
                any(),
                anyString(),
                anyString(),
                any(),
                any(Duration.class),
                eq(EntryGrantBatchVO.class),
                any()
        )).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(6)).get());
    }

    @Test
    void insertsNormalizedActiveGrantWithValidatedStudentIdentity() {
        SysUserDO leader = activeStudent();
        when(userMapper.selectById(21L)).thenReturn(leader);
        when(authorizationSnapshotService.loadRequired(21L)).thenReturn(snapshot("STUDENT"));
        when(entryGrantMapper.selectActiveByKeyForUpdate(10L, 20L, "STUDENT-001"))
                .thenReturn(null);
        when(entryGrantMapper.insert(any(EditionEntryGrantDO.class))).thenReturn(1);

        EntryGrantBatchVO result = service.batch(
                10L,
                request(upsertItem("21")),
                "grant-key"
        );

        assertThat(result).isEqualTo(new EntryGrantBatchVO(1, 0));
        ArgumentCaptor<EditionEntryGrantDO> captor =
                ArgumentCaptor.forClass(EditionEntryGrantDO.class);
        verify(entryGrantMapper).insert(captor.capture());
        EditionEntryGrantDO inserted = captor.getValue();
        assertThat(inserted.getTargetEditionId()).isEqualTo(10L);
        assertThat(inserted.getGrantType()).isEqualTo("QUALIFIED");
        assertThat(inserted.getLeaderUserId()).isEqualTo(21L);
        assertThat(inserted.getLeaderName()).isEqualTo("张三");
        assertThat(inserted.getSchoolId()).isEqualTo(20L);
        assertThat(inserted.getStudentNo()).isEqualTo("STUDENT-001");
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
        assertThat(inserted.getCreatedBy()).isEqualTo(9L);
        verify(contestAuthorizationService).requireContestEventManageable(edition);
        verify(auditService).record(eq(9L), eq("EDITION_ENTRY_GRANT_BATCH"), any());
    }

    @Test
    void updatesExistingActiveGrantWithoutInsertingAnotherRow() {
        EditionEntryGrantDO existing = activeGrant(31L, 4);
        when(entryGrantMapper.selectActiveByKeyForUpdate(10L, 20L, "STUDENT-001"))
                .thenReturn(existing);
        when(entryGrantMapper.updateActiveRecord(existing)).thenReturn(1);

        EntryGrantBatchVO result = service.batch(10L, request(upsertItem(null)), "grant-key");

        assertThat(result).isEqualTo(new EntryGrantBatchVO(1, 0));
        assertThat(existing.getGrantType()).isEqualTo("QUALIFIED");
        assertThat(existing.getLeaderName()).isEqualTo("张三");
        verify(entryGrantMapper, never()).insert(any(EditionEntryGrantDO.class));
        verify(entryGrantMapper).updateActiveRecord(existing);
    }

    @Test
    void revokeRequiresMatchingVersion() {
        EditionEntryGrantDO existing = activeGrant(55L, 3);
        when(entryGrantMapper.selectActiveByIdForUpdate(55L)).thenReturn(existing);

        EntryGrantBatchItem item = new EntryGrantBatchItem();
        item.setAction("REVOKE");
        item.setGrantId("55");
        item.setVersion(2);

        assertThatThrownBy(() -> service.batch(10L, request(item), "grant-key"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode()));
        verify(entryGrantMapper, never()).revokeActiveRecord(any());
        verify(auditService, never()).record(any(), anyString(), any());
    }

    @Test
    void revokeUsesLockedVersionAndReturnsCount() {
        EditionEntryGrantDO existing = activeGrant(55L, 3);
        when(entryGrantMapper.selectActiveByIdForUpdate(55L)).thenReturn(existing);
        when(entryGrantMapper.revokeActiveRecord(existing)).thenReturn(1);

        EntryGrantBatchItem item = new EntryGrantBatchItem();
        item.setAction("REVOKE");
        item.setGrantId("55");
        item.setVersion(3);
        item.setReason("管理员撤销");

        EntryGrantBatchVO result = service.batch(10L, request(item), "grant-key");

        assertThat(result).isEqualTo(new EntryGrantBatchVO(0, 1));
        assertThat(existing.getStatus()).isEqualTo("REVOKED");
        assertThat(existing.getRevokedBy()).isEqualTo(9L);
        assertThat(existing.getReason()).isEqualTo("管理员撤销");
        verify(entryGrantMapper).revokeActiveRecord(existing);
    }

    @Test
    void rejectsDuplicateActiveKeysInsideOneBatch() {
        assertThatThrownBy(() -> service.batch(
                10L,
                request(upsertItem(null), upsertItem(null)),
                "grant-key"
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getCode())
                        .isEqualTo(ErrorCode.DUPLICATE_RELATION.getCode()));
        verifyNoInteractions(entryGrantMapper, idempotencyService);
    }

    @Test
    void scopedEventManagerDoesNotNeedPlatformGlobalScope() {
        when(entryGrantMapper.selectActiveByKeyForUpdate(10L, 20L, "STUDENT-001"))
                .thenReturn(null);
        when(entryGrantMapper.insert(any(EditionEntryGrantDO.class))).thenReturn(1);

        assertThat(service.batch(10L, request(upsertItem(null)), "grant-key").upsertedCount())
                .isEqualTo(1);
        verify(contestAuthorizationService).requireContestEventManageable(edition);
    }

    @Test
    void rejectsMutationOnArchivedEdition() {
        edition.setStatus(EditionStatus.ARCHIVED.getDatabaseValue());

        assertThatThrownBy(() -> service.batch(10L, request(upsertItem(null)), "grant-key"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode()));
        verify(entryGrantMapper, never()).insert(any(EditionEntryGrantDO.class));
    }

    @Test
    void allowsArchivedEditionQuery() {
        edition.setStatus(EditionStatus.ARCHIVED.getDatabaseValue());
        when(entryGrantMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.list(10L, null).items()).isEmpty();
        verify(contestAuthorizationService).requireContestEventManageable(edition);
    }

    @Test
    void rejectsDisabledLeaderUser() {
        SysUserDO leader = activeStudent();
        leader.setStatus(0);
        when(userMapper.selectById(21L)).thenReturn(leader);

        assertInvalidLeaderIdentity("21");
    }

    @Test
    void rejectsLeaderWithoutStudentRole() {
        when(userMapper.selectById(21L)).thenReturn(activeStudent());
        when(authorizationSnapshotService.loadRequired(21L)).thenReturn(snapshot("TEACHER"));

        assertInvalidLeaderIdentity("21");
    }

    @Test
    void rejectsUnverifiedLeaderSchool() {
        SysUserDO leader = activeStudent();
        leader.setSchoolVerificationStatus("PENDING");
        when(userMapper.selectById(21L)).thenReturn(leader);

        assertInvalidLeaderIdentity("21");
    }

    @Test
    void rejectsLeaderSchoolMismatch() {
        SysUserDO leader = activeStudent();
        leader.setSchoolId(21L);
        when(userMapper.selectById(21L)).thenReturn(leader);

        assertInvalidLeaderIdentity("21");
    }

    @Test
    void rejectsLeaderStudentNoMismatch() {
        SysUserDO leader = activeStudent();
        leader.setStudentNo("OTHER-STUDENT");
        when(userMapper.selectById(21L)).thenReturn(leader);

        assertInvalidLeaderIdentity("21");
    }

    @Test
    void rejectsNonSchoolOrDisabledOrganization() {
        OrganizationDO organization = activeSchool();
        organization.setOrgType("PROVINCE");
        when(organizationMapper.selectById(20L)).thenReturn(organization);

        assertInvalidLeaderIdentity(null);
    }

    @Test
    void listsActiveAndRevokedRecordsWithContractFieldNames() {
        EditionEntryGrantDO active = activeGrant(31L, 0);
        EditionEntryGrantDO revoked = activeGrant(32L, 1);
        revoked.setStatus("REVOKED");
        revoked.setTargetTeamId(88L);
        when(entryGrantMapper.selectList(any())).thenReturn(List.of(active, revoked));

        EntryGrantListVO result = service.list(10L, null);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).id()).isEqualTo("31");
        assertThat(result.items().get(0).targetTeamId()).isNull();
        assertThat(result.items().get(1).status()).isEqualTo("REVOKED");
        assertThat(result.items().get(1).targetTeamId()).isEqualTo("88");
        verify(entryGrantMapper).selectList(any());
    }

    private void assertInvalidLeaderIdentity(String leaderUserId) {
        assertThatThrownBy(() -> service.batch(
                10L,
                request(upsertItem(leaderUserId)),
                "grant-key"
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getCode())
                        .isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
        verify(entryGrantMapper, never()).insert(any(EditionEntryGrantDO.class));
    }

    private EntryGrantBatchRequest request(EntryGrantBatchItem... items) {
        EntryGrantBatchRequest request = new EntryGrantBatchRequest();
        request.setItems(List.of(items));
        return request;
    }

    private EntryGrantBatchItem upsertItem(String leaderUserId) {
        EntryGrantBatchItem item = new EntryGrantBatchItem();
        item.setAction("upsert");
        item.setGrantType("qualified");
        item.setLeaderUserId(leaderUserId);
        item.setLeaderName(" 张三 ");
        item.setSchoolId("20");
        item.setStudentNo(" student-001 ");
        return item;
    }

    private SysUserDO activeStudent() {
        SysUserDO user = new SysUserDO();
        user.setId(21L);
        user.setStatus(1);
        user.setSchoolId(20L);
        user.setSchoolVerificationStatus("VERIFIED");
        user.setStudentNo("student-001");
        return user;
    }

    private OrganizationDO activeSchool() {
        OrganizationDO school = new OrganizationDO();
        school.setId(20L);
        school.setOrgType("SCHOOL");
        school.setStatus(1);
        return school;
    }

    private AuthorizationSnapshot snapshot(String... roles) {
        return new AuthorizationSnapshot(
                21L,
                "student",
                1,
                Set.of(roles),
                Set.of(),
                Set.of("ALL")
        );
    }

    private EditionEntryGrantDO activeGrant(Long id, int version) {
        EditionEntryGrantDO record = new EditionEntryGrantDO();
        record.setId(id);
        record.setTargetEditionId(10L);
        record.setGrantType("INVITED");
        record.setLeaderName("李四");
        record.setSchoolId(20L);
        record.setStudentNo("STUDENT-001");
        record.setStatus("ACTIVE");
        record.setVersion(version);
        record.setDeleted(0);
        return record;
    }
}
