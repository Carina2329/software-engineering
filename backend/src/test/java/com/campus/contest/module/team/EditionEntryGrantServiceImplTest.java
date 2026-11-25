package com.campus.contest.module.team;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.mapper.ContestEditionMapper;
import com.campus.contest.module.team.dto.EntryGrantBatchItem;
import com.campus.contest.module.team.dto.EntryGrantBatchRequest;
import com.campus.contest.module.team.entity.EditionEntryGrantDO;
import com.campus.contest.module.team.mapper.EditionEntryGrantMapper;
import com.campus.contest.module.team.service.TeamAuditService;
import com.campus.contest.module.team.service.TeamAuthorizationService;
import com.campus.contest.module.team.service.impl.EditionEntryGrantServiceImpl;
import com.campus.contest.module.team.vo.EntryGrantBatchVO;
import com.campus.contest.module.team.vo.EntryGrantListVO;
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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
    private TeamAuthorizationService authorizationService;
    @Mock
    private TeamAuditService auditService;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private EditionEntryGrantServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-11-25T02:00:00Z"),
                ZoneId.of("Asia/Shanghai")
        );
        service = new EditionEntryGrantServiceImpl(
                entryGrantMapper,
                editionMapper,
                authorizationService,
                auditService,
                idempotencyService,
                transactionTemplate,
                clock
        );
        lenient().when(editionMapper.selectById(10L)).thenReturn(new ContestEditionDO());
        lenient().when(authorizationService.currentUserId()).thenReturn(9L);
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
    void insertsNormalizedActiveGrantAndWritesAudit() {
        when(entryGrantMapper.selectActiveByKeyForUpdate(10L, 20L, "STUDENT-001"))
                .thenReturn(null);
        when(entryGrantMapper.insert(any(EditionEntryGrantDO.class))).thenReturn(1);

        EntryGrantBatchVO result = service.batch(10L, request(upsertItem()), "grant-key");

        assertThat(result).isEqualTo(new EntryGrantBatchVO(1, 0));
        ArgumentCaptor<EditionEntryGrantDO> captor =
                ArgumentCaptor.forClass(EditionEntryGrantDO.class);
        verify(entryGrantMapper).insert(captor.capture());
        EditionEntryGrantDO inserted = captor.getValue();
        assertThat(inserted.getTargetEditionId()).isEqualTo(10L);
        assertThat(inserted.getGrantType()).isEqualTo("QUALIFIED");
        assertThat(inserted.getLeaderName()).isEqualTo("张三");
        assertThat(inserted.getSchoolId()).isEqualTo(20L);
        assertThat(inserted.getStudentNo()).isEqualTo("STUDENT-001");
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
        assertThat(inserted.getCreatedBy()).isEqualTo(9L);
        verify(auditService).record(eq(9L), eq("EDITION_ENTRY_GRANT_BATCH"), any());
    }

    @Test
    void updatesExistingActiveGrantWithoutInsertingAnotherRow() {
        EditionEntryGrantDO existing = activeGrant(31L, 4);
        when(entryGrantMapper.selectActiveByKeyForUpdate(10L, 20L, "STUDENT-001"))
                .thenReturn(existing);
        when(entryGrantMapper.updateActiveRecord(existing)).thenReturn(1);

        EntryGrantBatchVO result = service.batch(10L, request(upsertItem()), "grant-key");

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
                request(upsertItem(), upsertItem()),
                "grant-key"
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getCode())
                        .isEqualTo(ErrorCode.DUPLICATE_RELATION.getCode()));
        verifyNoInteractions(entryGrantMapper, idempotencyService);
    }

    @Test
    void rejectsNonGlobalOperatorBeforeWriting() {
        doThrow(new BizException(
                ErrorCode.DATA_FORBIDDEN.getCode(),
                "当前操作仅允许平台全局管理员"
        )).when(authorizationService).requireGlobalOperator();

        assertThatThrownBy(() -> service.batch(10L, request(upsertItem()), "grant-key"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.DATA_FORBIDDEN.getCode()));
        verifyNoInteractions(entryGrantMapper, idempotencyService, auditService);
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

    private EntryGrantBatchRequest request(EntryGrantBatchItem... items) {
        EntryGrantBatchRequest request = new EntryGrantBatchRequest();
        request.setItems(List.of(items));
        return request;
    }

    private EntryGrantBatchItem upsertItem() {
        EntryGrantBatchItem item = new EntryGrantBatchItem();
        item.setAction("upsert");
        item.setGrantType("qualified");
        item.setLeaderName(" 张三 ");
        item.setSchoolId("20");
        item.setStudentNo(" student-001 ");
        return item;
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
