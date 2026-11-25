package com.campus.contest.module.team.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyScope;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.mapper.ContestEditionMapper;
import com.campus.contest.module.team.dto.EntryGrantBatchItem;
import com.campus.contest.module.team.dto.EntryGrantBatchRequest;
import com.campus.contest.module.team.entity.EditionEntryGrantDO;
import com.campus.contest.module.team.mapper.EditionEntryGrantMapper;
import com.campus.contest.module.team.service.EditionEntryGrantService;
import com.campus.contest.module.team.service.TeamAuditService;
import com.campus.contest.module.team.service.TeamAuthorizationService;
import com.campus.contest.module.team.vo.EntryGrantBatchVO;
import com.campus.contest.module.team.vo.EntryGrantListVO;
import com.campus.contest.module.team.vo.EntryGrantVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EditionEntryGrantServiceImpl implements EditionEntryGrantService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final int MAX_BATCH_SIZE = 200;
    private static final Set<String> ACTIONS = Set.of("UPSERT", "REVOKE");
    private static final Set<String> GRANT_TYPES = Set.of("INVITED", "QUALIFIED");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "REVOKED");

    private final EditionEntryGrantMapper entryGrantMapper;
    private final ContestEditionMapper editionMapper;
    private final TeamAuthorizationService authorizationService;
    private final TeamAuditService auditService;
    private final IdempotencyService idempotencyService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Override
    public EntryGrantBatchVO batch(
            Long editionId,
            EntryGrantBatchRequest request,
            String idempotencyKey
    ) {
        requireIdempotencyKey(idempotencyKey);
        List<NormalizedGrantItem> items = validateAndNormalize(editionId, request);
        authorizationService.requireGlobalOperator();
        requireEdition(editionId);
        Long operatorId = authorizationService.currentUserId();

        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("edition_id", editionId);
        fingerprint.put("items", items);

        try {
            EntryGrantBatchVO result = idempotencyService.execute(
                    new IdempotencyScope(
                            IdempotencyScope.PrincipalType.USER,
                            String.valueOf(operatorId)
                    ),
                    "EDITION_ENTRY_GRANT_BATCH",
                    idempotencyKey,
                    fingerprint,
                    IDEMPOTENCY_TTL,
                    EntryGrantBatchVO.class,
                    () -> transactionTemplate.execute(status -> doBatch(editionId, items, operatorId))
            );
            if (result == null) {
                throw new IllegalStateException("资格批量维护事务未返回结果");
            }
            return result;
        } catch (DuplicateKeyException exception) {
            throw new BizException(
                    ErrorCode.DUPLICATE_RELATION.getCode(),
                    "同一届次、学校和学号只能存在一条有效资格"
            );
        }
    }

    @Override
    public EntryGrantListVO list(Long editionId, String status) {
        authorizationService.requireGlobalOperator();
        requireEdition(editionId);
        String normalizedStatus = normalizeStatus(status);
        var query = Wrappers.<EditionEntryGrantDO>lambdaQuery()
                .eq(EditionEntryGrantDO::getTargetEditionId, editionId)
                .eq(normalizedStatus != null, EditionEntryGrantDO::getStatus, normalizedStatus)
                .orderByDesc(EditionEntryGrantDO::getCreatedAt)
                .orderByDesc(EditionEntryGrantDO::getId);
        List<EntryGrantVO> rows = entryGrantMapper.selectList(query).stream()
                .map(this::toVO)
                .toList();
        return new EntryGrantListVO(rows);
    }

    private EntryGrantBatchVO doBatch(
            Long editionId,
            List<NormalizedGrantItem> items,
            Long operatorId
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        int upsertedCount = 0;
        int revokedCount = 0;
        for (NormalizedGrantItem item : items) {
            if ("UPSERT".equals(item.action())) {
                upsert(editionId, item, operatorId, now);
                upsertedCount++;
            } else {
                revoke(editionId, item, operatorId, now);
                revokedCount++;
            }
        }
        auditService.record(operatorId, "EDITION_ENTRY_GRANT_BATCH", Map.of(
                "edition_id", String.valueOf(editionId),
                "item_count", String.valueOf(items.size()),
                "upserted_count", String.valueOf(upsertedCount),
                "revoked_count", String.valueOf(revokedCount)
        ));
        return new EntryGrantBatchVO(upsertedCount, revokedCount);
    }

    private void upsert(
            Long editionId,
            NormalizedGrantItem item,
            Long operatorId,
            LocalDateTime now
    ) {
        EditionEntryGrantDO existing = entryGrantMapper.selectActiveByKeyForUpdate(
                editionId,
                item.schoolId(),
                item.studentNo()
        );
        if (existing == null) {
            EditionEntryGrantDO record = new EditionEntryGrantDO();
            record.setTargetEditionId(editionId);
            record.setGrantType(item.grantType());
            record.setLeaderUserId(item.leaderUserId());
            record.setLeaderName(item.leaderName());
            record.setSchoolId(item.schoolId());
            record.setStudentNo(item.studentNo());
            record.setSourceEditionId(item.sourceEditionId());
            record.setSourceTeamCode(item.sourceTeamCode());
            record.setStatus("ACTIVE");
            record.setReason(item.reason());
            record.setCreatedAt(now);
            record.setCreatedBy(operatorId);
            record.setUpdatedAt(now);
            record.setUpdatedBy(operatorId);
            record.setDeleted(0);
            record.setVersion(0);
            if (entryGrantMapper.insert(record) != 1) {
                throw new IllegalStateException("资格记录写入失败");
            }
            return;
        }

        existing.setGrantType(item.grantType());
        existing.setLeaderUserId(item.leaderUserId());
        existing.setLeaderName(item.leaderName());
        existing.setSourceEditionId(item.sourceEditionId());
        existing.setSourceTeamCode(item.sourceTeamCode());
        existing.setReason(item.reason());
        existing.setUpdatedAt(now);
        existing.setUpdatedBy(operatorId);
        if (entryGrantMapper.updateActiveRecord(existing) != 1) {
            throw versionConflict();
        }
    }

    private void revoke(
            Long editionId,
            NormalizedGrantItem item,
            Long operatorId,
            LocalDateTime now
    ) {
        EditionEntryGrantDO existing = entryGrantMapper.selectActiveByIdForUpdate(item.grantId());
        if (existing == null
                || !editionId.equals(existing.getTargetEditionId())
                || !"ACTIVE".equals(existing.getStatus())
                || !item.version().equals(existing.getVersion())) {
            throw new BizException(
                    ErrorCode.BUSINESS_CONFLICT.getCode(),
                    "资格记录不存在、已撤销或版本已变化"
            );
        }
        existing.setStatus("REVOKED");
        existing.setReason(item.reason());
        existing.setUpdatedAt(now);
        existing.setUpdatedBy(operatorId);
        existing.setRevokedAt(now);
        existing.setRevokedBy(operatorId);
        if (entryGrantMapper.revokeActiveRecord(existing) != 1) {
            throw new BizException(
                    ErrorCode.BUSINESS_CONFLICT.getCode(),
                    "资格记录已被其他操作修改，请刷新后重试"
            );
        }
    }

    private List<NormalizedGrantItem> validateAndNormalize(
            Long editionId,
            EntryGrantBatchRequest request
    ) {
        if (editionId == null || editionId <= 0) {
            throw invalidParam("目标届次 ID 必须为正整数");
        }
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw invalidParam("items 至少包含一条记录");
        }
        if (request.getItems().size() > MAX_BATCH_SIZE) {
            throw invalidParam("items 最多包含 200 条记录");
        }

        List<NormalizedGrantItem> normalized = new ArrayList<>();
        Set<String> activeKeys = new HashSet<>();
        Set<Long> revokeIds = new HashSet<>();
        for (int index = 0; index < request.getItems().size(); index++) {
            EntryGrantBatchItem item = request.getItems().get(index);
            String prefix = "items[" + index + "]";
            if (item == null) {
                throw invalidParam(prefix + " 不能为空");
            }
            String action = normalizeEnum(item.getAction(), "action", prefix);
            if (!ACTIONS.contains(action)) {
                throw invalidParam(prefix + ".action 仅支持 UPSERT 或 REVOKE");
            }
            String reason = normalizeOptional(item.getReason(), 500, prefix + ".reason");
            if ("REVOKE".equals(action)) {
                Long grantId = parseRequiredId(item.getGrantId(), prefix + ".grant_id");
                if (item.getVersion() == null || item.getVersion() < 0) {
                    throw invalidParam(prefix + ".version 必须为非负整数");
                }
                if (!revokeIds.add(grantId)) {
                    throw new BizException(
                            ErrorCode.DUPLICATE_RELATION.getCode(),
                            prefix + ".grant_id 在本次请求中重复"
                    );
                }
                normalized.add(NormalizedGrantItem.revoke(grantId, item.getVersion(), reason));
                continue;
            }

            String grantType = normalizeEnum(item.getGrantType(), "grant_type", prefix);
            if (!GRANT_TYPES.contains(grantType)) {
                throw invalidParam(prefix + ".grant_type 仅支持 INVITED 或 QUALIFIED");
            }
            Long leaderUserId = parseOptionalId(item.getLeaderUserId(), prefix + ".leader_user_id");
            String leaderName = normalizeRequired(item.getLeaderName(), 32, prefix + ".leader_name");
            Long schoolId = parseRequiredId(item.getSchoolId(), prefix + ".school_id");
            String studentNo = normalizeStudentNo(item.getStudentNo(), prefix + ".student_no");
            Long sourceEditionId = parseOptionalId(
                    item.getSourceEditionId(),
                    prefix + ".source_edition_id"
            );
            String sourceTeamCode = normalizeOptional(
                    item.getSourceTeamCode(),
                    16,
                    prefix + ".source_team_code"
            );
            String key = schoolId + ":" + studentNo;
            if (!activeKeys.add(key)) {
                throw new BizException(
                        ErrorCode.DUPLICATE_RELATION.getCode(),
                        prefix + " 与本次请求中的其他有效资格重复"
                );
            }
            normalized.add(new NormalizedGrantItem(
                    "UPSERT",
                    null,
                    null,
                    grantType,
                    leaderUserId,
                    leaderName,
                    schoolId,
                    studentNo,
                    sourceEditionId,
                    sourceTeamCode,
                    reason
            ));
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return null;
        }
        if (status.isBlank()) {
            throw invalidParam("status 仅支持 ACTIVE 或 REVOKED");
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw invalidParam("status 仅支持 ACTIVE 或 REVOKED");
        }
        return normalized;
    }

    private void requireEdition(Long editionId) {
        ContestEditionDO edition = editionMapper.selectById(editionId);
        if (edition == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND.getCode(), "届次不存在");
        }
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 64) {
            throw invalidParam("Idempotency-Key 必填且不能超过 64 个字符");
        }
    }

    private String normalizeEnum(String value, String fieldName, String prefix) {
        if (value == null || value.isBlank()) {
            throw invalidParam(prefix + "." + fieldName + " 不能为空");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw invalidParam(fieldName + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw invalidParam(fieldName + " 不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw invalidParam(fieldName + " 不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String normalizeStudentNo(String value, String fieldName) {
        return normalizeRequired(value, 20, fieldName).toUpperCase(Locale.ROOT);
    }

    private Long parseRequiredId(String value, String fieldName) {
        Long id = parseOptionalId(value, fieldName);
        if (id == null) {
            throw invalidParam(fieldName + " 必须为正整数");
        }
        return id;
    }

    private Long parseOptionalId(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw invalidParam(fieldName + " 必须为正整数");
        }
    }

    private EntryGrantVO toVO(EditionEntryGrantDO record) {
        return new EntryGrantVO(
                String.valueOf(record.getId()),
                record.getGrantType(),
                idToString(record.getLeaderUserId()),
                record.getLeaderName(),
                String.valueOf(record.getSchoolId()),
                record.getStudentNo(),
                idToString(record.getSourceEditionId()),
                record.getSourceTeamCode(),
                idToString(record.getTargetTeamId()),
                record.getStatus(),
                record.getReason(),
                record.getVersion()
        );
    }

    private String idToString(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private BizException invalidParam(String message) {
        return new BizException(ErrorCode.INVALID_PARAM.getCode(), message);
    }

    private BizException versionConflict() {
        return new BizException(
                ErrorCode.BUSINESS_CONFLICT.getCode(),
                "资格记录已被其他操作修改，请刷新后重试"
        );
    }

    private record NormalizedGrantItem(
            String action,
            Long grantId,
            Integer version,
            String grantType,
            Long leaderUserId,
            String leaderName,
            Long schoolId,
            String studentNo,
            Long sourceEditionId,
            String sourceTeamCode,
            String reason
    ) {
        private static NormalizedGrantItem revoke(Long grantId, Integer version, String reason) {
            return new NormalizedGrantItem(
                    "REVOKE",
                    grantId,
                    version,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    reason
            );
        }
    }
}
