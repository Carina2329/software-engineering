package com.campus.contest.module.contest.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyScope;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.contest.dto.MilestoneCreateRequest;
import com.campus.contest.module.contest.dto.MilestoneUpdateRequest;
import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.entity.ContestMilestoneDO;
import com.campus.contest.module.contest.enums.EditionStatus;
import com.campus.contest.module.contest.enums.MilestoneNodeType;
import com.campus.contest.module.contest.mapper.ContestEditionMapper;
import com.campus.contest.module.contest.mapper.ContestMilestoneMapper;
import com.campus.contest.module.contest.service.ContestAuditService;
import com.campus.contest.module.contest.service.ContestAuthorizationService;
import com.campus.contest.module.contest.service.MilestoneService;
import com.campus.contest.module.contest.vo.MilestoneCreateVO;
import com.campus.contest.module.contest.vo.MilestoneVO;
import com.campus.contest.module.contest.vo.MilestoneVersionVO;
import com.campus.contest.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class MilestoneServiceImpl implements MilestoneService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final ZoneOffset SHANGHAI_OFFSET = ZoneOffset.ofHours(8);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final ContestEditionMapper editionMapper;
    private final ContestMilestoneMapper milestoneMapper;
    private final ContestAuthorizationService authorizationService;
    private final IdempotencyService idempotencyService;
    private final CurrentUserProvider currentUserProvider;
    private final ContestAuditService auditService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public List<MilestoneVO> list(Long editionId) {
        requireVisibleEdition(editionId);
        return milestoneMapper.selectList(
                        Wrappers.<ContestMilestoneDO>lambdaQuery()
                                .eq(ContestMilestoneDO::getEditionId, editionId)
                                .orderByAsc(ContestMilestoneDO::getStartTime)
                                .orderByAsc(ContestMilestoneDO::getId)
                ).stream()
                .map(MilestoneVO::from)
                .toList();
    }

    @Override
    public MilestoneCreateVO create(
            Long editionId,
            MilestoneCreateRequest request,
            String idempotencyKey
    ) {
        requireEditableEdition(editionId);
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        MilestoneNodeType nodeType = parseNodeType(request.getNodeType());
        String nodeName = requiredText(request.getNodeName());
        LocalDateTime start = toBusinessTime(request.getStartTime());
        LocalDateTime end = toBusinessTime(request.getEndTime());
        requireIdempotencyKey(idempotencyKey);
        Long operatorId = currentUserProvider.requireUserId();

        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("edition_id", String.valueOf(editionId));
        fingerprint.put("node_type", nodeType.name());
        fingerprint.put("node_name", nodeName);
        fingerprint.put("start_time", start);
        fingerprint.put("end_time", end);
        fingerprint.put("enabled", request.getEnabled());

        return executeIdempotently(
                operatorId, "EDITION_MILESTONE_CREATE", idempotencyKey,
                fingerprint, MilestoneCreateVO.class,
                () -> doCreate(editionId, nodeType, nodeName, start, end,
                        !Boolean.FALSE.equals(request.getEnabled()), operatorId)
        );
    }

    @Override
    public MilestoneVersionVO update(
            Long milestoneId,
            MilestoneUpdateRequest request,
            String idempotencyKey
    ) {
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        requireVersion(request.getVersion());
        if (request.getNodeName() == null
                && request.getStartTime() == null
                && request.getEndTime() == null
                && request.getEnabled() == null) {
            throw invalidParam("至少提交一个需要修改的赛程节点字段");
        }
        requireIdempotencyKey(idempotencyKey);
        String nodeName = request.getNodeName() == null ? null : requiredText(request.getNodeName());
        Long operatorId = currentUserProvider.requireUserId();

        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("milestone_id", String.valueOf(milestoneId));
        fingerprint.put("node_name", nodeName);
        fingerprint.put("start_time", toBusinessTime(request.getStartTime()));
        fingerprint.put("end_time", toBusinessTime(request.getEndTime()));
        fingerprint.put("enabled", request.getEnabled());
        fingerprint.put("version", request.getVersion());

        return executeIdempotently(
                operatorId, "EDITION_MILESTONE_UPDATE", idempotencyKey,
                fingerprint, MilestoneVersionVO.class,
                () -> doUpdate(milestoneId, request, nodeName, operatorId)
        );
    }

    private MilestoneCreateVO doCreate(
            Long editionId,
            MilestoneNodeType nodeType,
            String nodeName,
            LocalDateTime start,
            LocalDateTime end,
            boolean enabled,
            Long operatorId
    ) {
        return transactionTemplate.execute(status -> {
            ContestEditionDO edition = requireEditableEdition(editionId);
            validateNode(edition, nodeType, start, end);
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            ContestMilestoneDO entity = new ContestMilestoneDO();
            entity.setEditionId(editionId);
            entity.setNodeType(nodeType.name());
            entity.setNodeName(nodeName);
            entity.setStartTime(start);
            entity.setEndTime(end);
            entity.setEnabled(enabled ? 1 : 0);
            entity.setCreatedAt(now);
            entity.setCreatedBy(operatorId);
            entity.setUpdatedAt(now);
            entity.setUpdatedBy(operatorId);
            entity.setDeleted(0);
            entity.setVersion(0);
            milestoneMapper.insert(entity);
            auditService.record(operatorId, "EDITION_MILESTONE_CREATE", Map.of(
                    "edition_id", String.valueOf(editionId),
                    "milestone_id", String.valueOf(entity.getId()),
                    "node_type", nodeType.name()
            ));
            return new MilestoneCreateVO(String.valueOf(entity.getId()));
        });
    }

    private MilestoneVersionVO doUpdate(
            Long milestoneId,
            MilestoneUpdateRequest request,
            String nodeName,
            Long operatorId
    ) {
        return transactionTemplate.execute(status -> {
            ContestMilestoneDO existing = requireMilestone(milestoneId);
            if (!Objects.equals(existing.getVersion(), request.getVersion())) {
                throw versionConflict();
            }
            ContestEditionDO edition = requireEditableEdition(existing.getEditionId());
            LocalDateTime start = request.getStartTime() == null
                    ? existing.getStartTime()
                    : toBusinessTime(request.getStartTime());
            LocalDateTime end = request.getEndTime() == null
                    ? existing.getEndTime()
                    : toBusinessTime(request.getEndTime());
            validateNode(edition, MilestoneNodeType.parse(existing.getNodeType()), start, end);

            ContestMilestoneDO update = new ContestMilestoneDO();
            update.setId(milestoneId);
            update.setNodeName(nodeName);
            update.setStartTime(request.getStartTime() == null ? null : start);
            update.setEndTime(request.getEndTime() == null ? null : end);
            update.setEnabled(request.getEnabled() == null ? null : (request.getEnabled() ? 1 : 0));
            update.setUpdatedAt(LocalDateTime.now(BUSINESS_ZONE));
            update.setUpdatedBy(operatorId);
            update.setVersion(request.getVersion());
            if (milestoneMapper.updateById(update) != 1) {
                throw versionConflict();
            }
            auditService.record(operatorId, "EDITION_MILESTONE_UPDATE", Map.of(
                    "milestone_id", String.valueOf(milestoneId),
                    "before_version", request.getVersion(),
                    "after_version", request.getVersion() + 1
            ));
            return new MilestoneVersionVO(String.valueOf(milestoneId), request.getVersion() + 1);
        });
    }

    private void validateNode(
            ContestEditionDO edition,
            MilestoneNodeType nodeType,
            LocalDateTime start,
            LocalDateTime end
    ) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw invalidParam("赛程节点开始时间必须早于结束时间");
        }
        switch (nodeType) {
            case REGISTER_END -> {
                if (edition.getWorkDeadline() != null && end.isAfter(edition.getWorkDeadline())) {
                    throw invalidParam("报名结束节点不能晚于作品截止时间");
                }
            }
            case WORK_END -> {
                if (edition.getRegisterDeadline() != null && start.isBefore(edition.getRegisterDeadline())) {
                    throw invalidParam("作品结束节点不能早于报名截止时间");
                }
                if (edition.getPublishStart() != null && end.isAfter(edition.getPublishStart())) {
                    throw invalidParam("作品结束节点不能晚于公示开始时间");
                }
            }
            case REVIEW -> {
                if (edition.getWorkDeadline() != null && start.isBefore(edition.getWorkDeadline())) {
                    throw invalidParam("评审节点不能早于作品截止时间");
                }
                if (edition.getPublishEnd() != null && end.isAfter(edition.getPublishEnd())) {
                    throw invalidParam("评审节点不能晚于公示结束时间");
                }
            }
            case PUBLISH -> {
                if (edition.getReviewStart() != null && start.isBefore(edition.getReviewStart())) {
                    throw invalidParam("公示节点不能早于评审开始时间");
                }
                if (edition.getPublishEnd() != null && end.isAfter(edition.getPublishEnd())) {
                    throw invalidParam("公示节点不能晚于公示结束时间");
                }
            }
        }
    }

    private ContestEditionDO requireVisibleEdition(Long editionId) {
        ContestEditionDO edition = requireEdition(editionId);
        if (!authorizationService.canManage()
                && !EditionStatus.fromDatabaseValue(edition.getStatus()).isVisibleToOrdinaryUser()) {
            throw notFound("届次不存在或对当前用户不可见");
        }
        return edition;
    }

    private ContestEditionDO requireEditableEdition(Long editionId) {
        ContestEditionDO edition = requireEdition(editionId);
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        if (EditionStatus.fromDatabaseValue(edition.getStatus()) == EditionStatus.ARCHIVED
                || (edition.getPublishEnd() != null && now.isAfter(edition.getPublishEnd()))) {
            throw new BizException(
                    ErrorCode.INVALID_STATE_TRANSITION.getCode(),
                    "届次已只读，不能修改赛程节点"
            );
        }
        return edition;
    }

    private ContestEditionDO requireEdition(Long editionId) {
        ContestEditionDO edition = editionId == null ? null : editionMapper.selectById(editionId);
        if (edition == null) {
            throw notFound("届次不存在或对当前用户不可见");
        }
        return edition;
    }

    private ContestMilestoneDO requireMilestone(Long milestoneId) {
        ContestMilestoneDO milestone =
                milestoneId == null ? null : milestoneMapper.selectById(milestoneId);
        if (milestone == null) {
            throw notFound("赛程节点不存在或对当前用户不可见");
        }
        return milestone;
    }

    private MilestoneNodeType parseNodeType(String value) {
        try {
            return MilestoneNodeType.parse(value);
        } catch (IllegalArgumentException exception) {
            throw invalidParam(exception.getMessage());
        }
    }

    private String requiredText(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > 64) {
            throw invalidParam("node_name 长度必须为 1-64");
        }
        return normalized;
    }

    private LocalDateTime toBusinessTime(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(SHANGHAI_OFFSET).toLocalDateTime();
    }

    private void requireVersion(Integer version) {
        if (version == null || version < 0) {
            throw invalidParam("version 必须为非负整数");
        }
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw invalidParam("Idempotency-Key 必须为 1-64 位非空字符串");
        }
    }

    private <T> T executeIdempotently(
            Long operatorId,
            String operation,
            String idempotencyKey,
            Object fingerprint,
            Class<T> resultType,
            Supplier<T> action
    ) {
        return idempotencyService.execute(
                new IdempotencyScope(IdempotencyScope.PrincipalType.USER, String.valueOf(operatorId)),
                operation,
                idempotencyKey,
                fingerprint,
                IDEMPOTENCY_TTL,
                resultType,
                action
        );
    }

    private BizException invalidParam(String message) {
        return new BizException(ErrorCode.INVALID_PARAM.getCode(), message);
    }

    private BizException notFound(String message) {
        return new BizException(ErrorCode.RESOURCE_NOT_FOUND.getCode(), message);
    }

    private BizException versionConflict() {
        return new BizException(
                ErrorCode.BUSINESS_CONFLICT.getCode(),
                "数据已被他人修改，请刷新后重试"
        );
    }
}
