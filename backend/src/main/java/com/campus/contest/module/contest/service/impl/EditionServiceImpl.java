package com.campus.contest.module.contest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyScope;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.contest.dto.EditionCreateRequest;
import com.campus.contest.module.contest.dto.EditionPageQuery;
import com.campus.contest.module.contest.dto.EditionPublishRequest;
import com.campus.contest.module.contest.dto.EditionUpdateRequest;
import com.campus.contest.module.contest.entity.ContestDO;
import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.enums.EditionStatus;
import com.campus.contest.module.contest.enums.TeamSizeMode;
import com.campus.contest.module.contest.enums.TopicAllocationMode;
import com.campus.contest.module.contest.enums.TopicMode;
import com.campus.contest.module.contest.mapper.ContestEditionMapper;
import com.campus.contest.module.contest.mapper.ContestMapper;
import com.campus.contest.module.contest.mapper.ContestTeamMapper;
import com.campus.contest.module.contest.service.ContestAuditService;
import com.campus.contest.module.contest.service.ContestAuthorizationService;
import com.campus.contest.module.contest.service.EditionService;
import com.campus.contest.module.contest.vo.EditionCreateVO;
import com.campus.contest.module.contest.vo.EditionDetailVO;
import com.campus.contest.module.contest.vo.EditionItemVO;
import com.campus.contest.module.contest.vo.EditionPageVO;
import com.campus.contest.module.contest.vo.EditionPublishVO;
import com.campus.contest.module.contest.vo.EditionVersionVO;
import com.campus.contest.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
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
public class EditionServiceImpl implements EditionService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final ZoneOffset SHANGHAI_OFFSET = ZoneOffset.ofHours(8);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final ContestMapper contestMapper;
    private final ContestEditionMapper editionMapper;
    private final ContestTeamMapper teamMapper;
    private final ContestAuthorizationService authorizationService;
    private final IdempotencyService idempotencyService;
    private final CurrentUserProvider currentUserProvider;
    private final ContestAuditService auditService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public EditionPageVO page(Long contestId, EditionPageQuery query) {
        requireContest(contestId);
        EditionPageQuery effective = query == null ? new EditionPageQuery() : query;
        int page = pageNumber(effective.getPage());
        int size = pageSize(effective.getSize());
        EditionStatus status = effective.getStatus() == null || effective.getStatus().isBlank()
                ? null
                : parseStatus(effective.getStatus());
        if (effective.getYear() != null && (effective.getYear() < 2000 || effective.getYear() > 2100)) {
            throw invalidParam("year 必须在 2000 到 2100 之间");
        }

        boolean manager = authorizationService.canManage();
        if (!manager) {
            if (status == EditionStatus.DRAFT) {
                return new EditionPageVO(List.of(), 0, page, size, false);
            }
            long publishedCount = editionMapper.selectCount(
                    Wrappers.<ContestEditionDO>lambdaQuery()
                            .eq(ContestEditionDO::getContestId, contestId)
                            .ne(ContestEditionDO::getStatus, EditionStatus.DRAFT.getDatabaseValue())
            );
            if (publishedCount == 0) {
                throw notFound("竞赛不存在或对当前用户不可见");
            }
        }

        LambdaQueryWrapper<ContestEditionDO> wrapper =
                Wrappers.<ContestEditionDO>lambdaQuery().eq(ContestEditionDO::getContestId, contestId);
        if (effective.getYear() != null) {
            wrapper.eq(ContestEditionDO::getYear, effective.getYear());
        }
        if (status != null) {
            wrapper.eq(ContestEditionDO::getStatus, status.getDatabaseValue());
        }
        if (!manager) {
            wrapper.ne(ContestEditionDO::getStatus, EditionStatus.DRAFT.getDatabaseValue());
        }
        applyEditionSort(wrapper, effective.getSort());

        IPage<ContestEditionDO> result = editionMapper.selectPage(new Page<>(page, size), wrapper);
        List<EditionItemVO> items = result.getRecords().stream().map(EditionItemVO::from).toList();
        return new EditionPageVO(items, result.getTotal(), page, size,
                (long) page * size < result.getTotal());
    }

    @Override
    public EditionCreateVO create(
            Long contestId,
            EditionCreateRequest request,
            String idempotencyKey
    ) {
        requireContest(contestId);
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        String editionName = requiredText(request.getEditionName(), "edition_name", 128);
        if (request.getYear() == null || request.getYear() < 2000 || request.getYear() > 2100) {
            throw invalidParam("year 必须在 2000 到 2100 之间");
        }
        TeamSizeMode teamSizeMode = parseTeamSizeMode(request.getTeamSizeMode());
        validateTeamSizes(teamSizeMode, request.getMinTeamSize(), request.getMaxTeamSize());
        int teacherLimit = request.getMaxTeamsPerTeacher() == null
                ? 3
                : request.getMaxTeamsPerTeacher();
        validateTeacherLimit(teacherLimit);
        TopicMode topicMode = parseTopicModeNullable(request.getTopicMode());
        TopicAllocationMode allocationMode = request.getTopicAllocationMode() == null
                ? TopicAllocationMode.REVIEW
                : parseAllocationMode(request.getTopicAllocationMode());
        int changeLimit = request.getTopicChangeLimit() == null
                ? 1
                : request.getTopicChangeLimit();
        validateTopicRules(topicMode, allocationMode, changeLimit);

        LocalDateTime registerDeadline = toBusinessTime(request.getRegisterDeadline());
        LocalDateTime workDeadline = toBusinessTime(request.getWorkDeadline());
        LocalDateTime reviewStart = toBusinessTime(request.getReviewStart());
        LocalDateTime publishStart = toBusinessTime(request.getPublishStart());
        LocalDateTime publishEnd = toBusinessTime(request.getPublishEnd());
        validateTimeOrderWhenComplete(
                registerDeadline, workDeadline, reviewStart, publishStart, publishEnd
        );

        requireIdempotencyKey(idempotencyKey);
        Long operatorId = currentUserProvider.requireUserId();
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("contest_id", String.valueOf(contestId));
        fingerprint.put("edition_name", editionName);
        fingerprint.put("year", request.getYear());
        fingerprint.put("team_size_mode", teamSizeMode.name());
        fingerprint.put("min_team_size", request.getMinTeamSize());
        fingerprint.put("max_team_size", request.getMaxTeamSize());
        fingerprint.put("max_teams_per_teacher", teacherLimit);
        fingerprint.put("topic_mode", topicMode == null ? null : topicMode.name());

        return executeIdempotently(
                operatorId, "EDITION_CREATE", idempotencyKey, fingerprint, EditionCreateVO.class,
                () -> doCreate(contestId, editionName, request, teamSizeMode, teacherLimit,
                        topicMode, allocationMode, changeLimit, registerDeadline, workDeadline,
                        reviewStart, publishStart, publishEnd, operatorId)
        );
    }

    @Override
    public EditionDetailVO get(Long editionId) {
        ContestEditionDO edition = requireVisibleEdition(editionId);
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        EditionStatus status = EditionStatus.fromDatabaseValue(edition.getStatus());
        boolean registrationOpen = status == EditionStatus.REGISTERING
                && edition.getRegisterDeadline() != null
                && !now.isAfter(edition.getRegisterDeadline());
        boolean submissionOpen = (status == EditionStatus.REGISTERING
                || status == EditionStatus.SUBMITTING)
                && edition.getWorkDeadline() != null
                && !now.isAfter(edition.getWorkDeadline());
        boolean readOnly = isReadOnly(edition, now);
        return EditionDetailVO.from(edition, registrationOpen, submissionOpen, readOnly);
    }

    @Override
    public EditionVersionVO update(
            Long editionId,
            EditionUpdateRequest request,
            String idempotencyKey
    ) {
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        requireVersion(request.getVersion());
        requireAtLeastOneChange(request);
        requireIdempotencyKey(idempotencyKey);

        String editionName = request.getEditionName() == null
                ? null
                : requiredText(request.getEditionName(), "edition_name", 128);
        TeamSizeMode teamSizeMode = request.getTeamSizeMode() == null
                ? null
                : parseTeamSizeMode(request.getTeamSizeMode());
        TopicMode topicMode = request.getTopicMode() == null
                ? null
                : parseTopicMode(request.getTopicMode());
        TopicAllocationMode allocationMode = request.getTopicAllocationMode() == null
                ? null
                : parseAllocationMode(request.getTopicAllocationMode());

        Long operatorId = currentUserProvider.requireUserId();
        Map<String, Object> fingerprint = updateFingerprint(editionId, request);
        return executeIdempotently(
                operatorId, "EDITION_UPDATE", idempotencyKey, fingerprint, EditionVersionVO.class,
                () -> doUpdate(editionId, request, editionName, teamSizeMode, topicMode,
                        allocationMode, operatorId)
        );
    }

    @Override
    public EditionPublishVO publish(
            Long editionId,
            EditionPublishRequest request,
            String idempotencyKey
    ) {
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        requireVersion(request.getVersion());
        requireIdempotencyKey(idempotencyKey);
        Long operatorId = currentUserProvider.requireUserId();
        Map<String, Object> fingerprint = Map.of(
                "edition_id", String.valueOf(editionId),
                "version", request.getVersion()
        );
        return executeIdempotently(
                operatorId, "EDITION_PUBLISH", idempotencyKey, fingerprint,
                EditionPublishVO.class,
                () -> doPublish(editionId, request.getVersion(), operatorId)
        );
    }

    private EditionCreateVO doCreate(
            Long contestId,
            String editionName,
            EditionCreateRequest request,
            TeamSizeMode teamSizeMode,
            int teacherLimit,
            TopicMode topicMode,
            TopicAllocationMode allocationMode,
            int changeLimit,
            LocalDateTime registerDeadline,
            LocalDateTime workDeadline,
            LocalDateTime reviewStart,
            LocalDateTime publishStart,
            LocalDateTime publishEnd,
            Long operatorId
    ) {
        return transactionTemplate.execute(status -> {
            ensureEditionNameAvailable(contestId, editionName, null);
            ContestEditionDO entity = new ContestEditionDO();
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            entity.setContestId(contestId);
            entity.setEditionName(editionName);
            entity.setYear(request.getYear());
            entity.setStatus(EditionStatus.DRAFT.getDatabaseValue());
            entity.setRegisterDeadline(registerDeadline);
            entity.setWorkDeadline(workDeadline);
            entity.setReviewStart(reviewStart);
            entity.setPublishStart(publishStart);
            entity.setPublishEnd(publishEnd);
            entity.setMinTeamSize(request.getMinTeamSize());
            entity.setMaxTeamSize(request.getMaxTeamSize());
            entity.setTeamSizeMode(teamSizeMode.name());
            entity.setMaxTeamsPerTeacher(teacherLimit);
            entity.setTopicMode(topicMode == null ? null : topicMode.name());
            entity.setTopicReviewRequired(Boolean.FALSE.equals(request.getTopicReviewRequired()) ? 0 : 1);
            entity.setTopicAllocationMode(allocationMode.name());
            entity.setTopicChangeLimit(changeLimit);
            entity.setCreatedAt(now);
            entity.setCreatedBy(operatorId);
            entity.setUpdatedAt(now);
            entity.setUpdatedBy(operatorId);
            entity.setDeleted(0);
            entity.setVersion(0);
            try {
                editionMapper.insert(entity);
            } catch (DuplicateKeyException exception) {
                throw duplicateEditionName();
            }
            auditService.record(operatorId, "EDITION_CREATE", Map.of(
                    "contest_id", String.valueOf(contestId),
                    "edition_id", String.valueOf(entity.getId()),
                    "edition_name", editionName
            ));
            return new EditionCreateVO(
                    String.valueOf(entity.getId()),
                    EditionStatus.DRAFT.name()
            );
        });
    }

    private EditionVersionVO doUpdate(
            Long editionId,
            EditionUpdateRequest request,
            String editionName,
            TeamSizeMode requestedTeamSizeMode,
            TopicMode requestedTopicMode,
            TopicAllocationMode requestedAllocationMode,
            Long operatorId
    ) {
        return transactionTemplate.execute(status -> {
            ContestEditionDO existing = requireEdition(editionId);
            if (!Objects.equals(existing.getVersion(), request.getVersion())) {
                throw versionConflict();
            }
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            if (isReadOnly(existing, now)) {
                throw invalidState("届次已只读，不能修改配置");
            }

            long teamCount = teamMapper.countByEditionId(editionId);
            TeamSizeMode existingMode = parseTeamSizeMode(existing.getTeamSizeMode());
            TopicMode existingTopicMode = parseTopicModeNullable(existing.getTopicMode());
            TeamSizeMode effectiveMode = requestedTeamSizeMode == null
                    ? existingMode
                    : requestedTeamSizeMode;
            TopicMode effectiveTopicMode = requestedTopicMode == null
                    ? existingTopicMode
                    : requestedTopicMode;
            int effectiveMin = request.getMinTeamSize() == null
                    ? existing.getMinTeamSize()
                    : request.getMinTeamSize();
            int effectiveMax = request.getMaxTeamSize() == null
                    ? existing.getMaxTeamSize()
                    : request.getMaxTeamSize();
            int effectiveTeacherLimit = request.getMaxTeamsPerTeacher() == null
                    ? existing.getMaxTeamsPerTeacher()
                    : request.getMaxTeamsPerTeacher();
            int effectiveChangeLimit = request.getTopicChangeLimit() == null
                    ? existing.getTopicChangeLimit()
                    : request.getTopicChangeLimit();
            TopicAllocationMode effectiveAllocation = requestedAllocationMode == null
                    ? TopicAllocationMode.valueOf(existing.getTopicAllocationMode())
                    : requestedAllocationMode;

            validateTeamSizes(effectiveMode, effectiveMin, effectiveMax);
            validateTeacherLimit(effectiveTeacherLimit);
            validateTopicRules(effectiveTopicMode, effectiveAllocation, effectiveChangeLimit);
            if (teamCount > 0) {
                ensureStructuralFieldsUnchanged(
                        existing, requestedTeamSizeMode, request.getMinTeamSize(),
                        request.getMaxTeamSize(), requestedTopicMode,
                        request.getTopicChangeLimit(), request.getMaxTeamsPerTeacher()
                );
            }

            LocalDateTime effectiveRegister = request.getRegisterDeadline() == null
                    ? existing.getRegisterDeadline()
                    : toBusinessTime(request.getRegisterDeadline());
            LocalDateTime effectiveWork = request.getWorkDeadline() == null
                    ? existing.getWorkDeadline()
                    : toBusinessTime(request.getWorkDeadline());
            LocalDateTime effectiveReview = request.getReviewStart() == null
                    ? existing.getReviewStart()
                    : toBusinessTime(request.getReviewStart());
            LocalDateTime effectivePublishStart = request.getPublishStart() == null
                    ? existing.getPublishStart()
                    : toBusinessTime(request.getPublishStart());
            LocalDateTime effectivePublishEnd = request.getPublishEnd() == null
                    ? existing.getPublishEnd()
                    : toBusinessTime(request.getPublishEnd());
            validateTimeOrderWhenComplete(
                    effectiveRegister, effectiveWork, effectiveReview,
                    effectivePublishStart, effectivePublishEnd
            );
            ensureDeadlinesOnlyExtended(existing, request, now);
            if (editionName != null) {
                ensureEditionNameAvailable(existing.getContestId(), editionName, editionId);
            }

            Map<String, Object> before = editionSnapshot(existing);
            ContestEditionDO update = new ContestEditionDO();
            update.setId(editionId);
            update.setEditionName(editionName);
            update.setRegisterDeadline(request.getRegisterDeadline() == null ? null : effectiveRegister);
            update.setWorkDeadline(request.getWorkDeadline() == null ? null : effectiveWork);
            update.setReviewStart(request.getReviewStart() == null ? null : effectiveReview);
            update.setPublishStart(request.getPublishStart() == null ? null : effectivePublishStart);
            update.setPublishEnd(request.getPublishEnd() == null ? null : effectivePublishEnd);
            update.setMinTeamSize(request.getMinTeamSize());
            update.setMaxTeamSize(request.getMaxTeamSize());
            update.setTeamSizeMode(requestedTeamSizeMode == null ? null : requestedTeamSizeMode.name());
            update.setMaxTeamsPerTeacher(request.getMaxTeamsPerTeacher());
            update.setTopicMode(requestedTopicMode == null ? null : requestedTopicMode.name());
            update.setTopicReviewRequired(
                    request.getTopicReviewRequired() == null
                            ? null
                            : (request.getTopicReviewRequired() ? 1 : 0)
            );
            update.setTopicAllocationMode(
                    requestedAllocationMode == null ? null : requestedAllocationMode.name()
            );
            update.setTopicChangeLimit(request.getTopicChangeLimit());
            update.setUpdatedAt(now);
            update.setUpdatedBy(operatorId);
            update.setVersion(request.getVersion());
            try {
                if (editionMapper.updateById(update) != 1) {
                    throw versionConflict();
                }
            } catch (DuplicateKeyException exception) {
                throw duplicateEditionName();
            }

            Map<String, Object> after = new LinkedHashMap<>();
            after.put("edition_id", String.valueOf(editionId));
            after.put("edition_name", editionName == null ? existing.getEditionName() : editionName);
            after.put("register_deadline", effectiveRegister);
            after.put("work_deadline", effectiveWork);
            after.put("team_size_mode", effectiveMode.name());
            after.put("min_team_size", effectiveMin);
            after.put("max_team_size", effectiveMax);
            after.put("max_teams_per_teacher", effectiveTeacherLimit);
            after.put("topic_mode", effectiveTopicMode == null ? null : effectiveTopicMode.name());
            after.put("topic_allocation_mode", effectiveAllocation.name());
            after.put("topic_change_limit", effectiveChangeLimit);
            after.put("version", request.getVersion() + 1);
            auditService.record(operatorId, "EDITION_UPDATE",
                    Map.of("before", before, "after", after));
            return new EditionVersionVO(String.valueOf(editionId), request.getVersion() + 1);
        });
    }

    private EditionPublishVO doPublish(Long editionId, Integer version, Long operatorId) {
        return transactionTemplate.execute(status -> {
            ContestEditionDO existing = requireEdition(editionId);
            if (!Objects.equals(existing.getVersion(), version)) {
                throw versionConflict();
            }
            EditionStatus current = EditionStatus.fromDatabaseValue(existing.getStatus());
            if (current == EditionStatus.REGISTERING) {
                return new EditionPublishVO(String.valueOf(editionId), current.name());
            }
            if (current != EditionStatus.DRAFT) {
                throw invalidState("第一阶段只允许草稿届次发布为报名中");
            }
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            validatePublishConfiguration(existing, now);

            ContestEditionDO update = new ContestEditionDO();
            update.setId(editionId);
            update.setStatus(EditionStatus.REGISTERING.getDatabaseValue());
            update.setUpdatedAt(now);
            update.setUpdatedBy(operatorId);
            update.setVersion(version);
            if (editionMapper.updateById(update) != 1) {
                throw versionConflict();
            }
            auditService.record(operatorId, "EDITION_PUBLISH", Map.of(
                    "edition_id", String.valueOf(editionId),
                    "from_status", current.name(),
                    "to_status", EditionStatus.REGISTERING.name(),
                    "version", version + 1
            ));
            return new EditionPublishVO(
                    String.valueOf(editionId),
                    EditionStatus.REGISTERING.name()
            );
        });
    }

    private void validatePublishConfiguration(ContestEditionDO edition, LocalDateTime now) {
        if (edition.getRegisterDeadline() == null
                || edition.getWorkDeadline() == null
                || edition.getReviewStart() == null
                || edition.getPublishStart() == null
                || edition.getPublishEnd() == null) {
            throw invalidParam("发布前必须配置完整的报名、作品、评审和公示时间");
        }
        validateTimeOrderWhenComplete(
                edition.getRegisterDeadline(), edition.getWorkDeadline(), edition.getReviewStart(),
                edition.getPublishStart(), edition.getPublishEnd()
        );
        if (!edition.getRegisterDeadline().isAfter(now)) {
            throw new BizException(
                    ErrorCode.BUSINESS_DEADLINE_PASSED.getCode(),
                    "报名截止时间必须晚于当前时间"
            );
        }
        TeamSizeMode mode = parseTeamSizeMode(edition.getTeamSizeMode());
        validateTeamSizes(mode, edition.getMinTeamSize(), edition.getMaxTeamSize());
        validateTeacherLimit(edition.getMaxTeamsPerTeacher());
        if (edition.getTopicMode() == null || edition.getTopicMode().isBlank()) {
            throw invalidParam("发布前必须选择赛题模式");
        }
        TopicMode topicMode = parseTopicMode(edition.getTopicMode());
        TopicAllocationMode allocationMode = parseAllocationMode(edition.getTopicAllocationMode());
        validateTopicRules(topicMode, allocationMode, edition.getTopicChangeLimit());
        if (!Integer.valueOf(0).equals(edition.getTopicReviewRequired())
                && !Integer.valueOf(1).equals(edition.getTopicReviewRequired())) {
            throw invalidParam("选题审核规则必须为 true 或 false");
        }
    }

    private void ensureStructuralFieldsUnchanged(
            ContestEditionDO existing,
            TeamSizeMode requestedMode,
            Integer requestedMin,
            Integer requestedMax,
            TopicMode requestedTopic,
            Integer requestedChangeLimit,
            Integer requestedTeacherLimit
    ) {
        boolean changesMode = requestedMode != null
                && requestedMode != parseTeamSizeMode(existing.getTeamSizeMode());
        boolean changesSizes = (requestedMin != null && !requestedMin.equals(existing.getMinTeamSize()))
                || (requestedMax != null && !requestedMax.equals(existing.getMaxTeamSize()));
        boolean changesTopic = requestedTopic != null
                && requestedTopic != parseTopicModeNullable(existing.getTopicMode());
        boolean changesChangeLimit = requestedChangeLimit != null
                && !requestedChangeLimit.equals(existing.getTopicChangeLimit());
        boolean changesTeacherLimit = requestedTeacherLimit != null
                && !requestedTeacherLimit.equals(existing.getMaxTeamsPerTeacher());
        if (changesMode || changesSizes || changesTopic || changesChangeLimit || changesTeacherLimit) {
            throw new BizException(
                    ErrorCode.BUSINESS_CONFLICT.getCode(),
                    "届次已产生队伍，不能修改人数规则、赛题模式、更换次数或教师指导上限"
            );
        }
    }

    private void ensureDeadlinesOnlyExtended(
            ContestEditionDO existing,
            EditionUpdateRequest request,
            LocalDateTime now
    ) {
        ensureNotShortened(existing.getRegisterDeadline(),
                toBusinessTime(request.getRegisterDeadline()), now, "报名截止时间");
        ensureNotShortened(existing.getWorkDeadline(),
                toBusinessTime(request.getWorkDeadline()), now, "作品截止时间");
        ensureNotShortened(existing.getReviewStart(),
                toBusinessTime(request.getReviewStart()), now, "评审开始时间");
        ensureNotShortened(existing.getPublishStart(),
                toBusinessTime(request.getPublishStart()), now, "公示开始时间");
        ensureNotShortened(existing.getPublishEnd(),
                toBusinessTime(request.getPublishEnd()), now, "公示结束时间");
    }

    private void ensureNotShortened(
            LocalDateTime existing,
            LocalDateTime requested,
            LocalDateTime now,
            String label
    ) {
        if (existing != null
                && requested != null
                && !existing.isAfter(now)
                && requested.isBefore(existing)) {
            throw new BizException(
                    ErrorCode.BUSINESS_DEADLINE_PASSED.getCode(),
                    label + "已进入对应阶段，只能延长，不能提前"
            );
        }
    }

    private void ensureEditionNameAvailable(Long contestId, String editionName, Long excludedId) {
        LambdaQueryWrapper<ContestEditionDO> wrapper =
                Wrappers.<ContestEditionDO>lambdaQuery()
                        .eq(ContestEditionDO::getContestId, contestId)
                        .eq(ContestEditionDO::getEditionName, editionName);
        if (excludedId != null) {
            wrapper.ne(ContestEditionDO::getId, excludedId);
        }
        if (editionMapper.selectCount(wrapper) > 0) {
            throw duplicateEditionName();
        }
    }

    private void validateTimeOrderWhenComplete(
            LocalDateTime registerDeadline,
            LocalDateTime workDeadline,
            LocalDateTime reviewStart,
            LocalDateTime publishStart,
            LocalDateTime publishEnd
    ) {
        if (registerDeadline == null || workDeadline == null || reviewStart == null
                || publishStart == null || publishEnd == null) {
            return;
        }
        if (!registerDeadline.isBefore(workDeadline)
                || !workDeadline.isBefore(reviewStart)
                || !reviewStart.isBefore(publishStart)
                || !publishStart.isBefore(publishEnd)) {
            throw invalidParam("核心时间必须满足：报名截止 < 作品截止 < 评审开始 < 公示开始 < 公示结束");
        }
    }

    private void validateTeamSizes(TeamSizeMode mode, Integer min, Integer max) {
        if (min == null || max == null || min < 1 || min > 5 || max < 1 || max > 5) {
            throw invalidParam("队伍最少和最多人数都必须在 1-5 之间");
        }
        if (min > max) {
            throw invalidParam("队伍最少人数不能大于最多人数");
        }
        if (mode == TeamSizeMode.FIXED && !min.equals(max)) {
            throw invalidParam("固定人数模式下最少人数必须等于最多人数");
        }
    }

    private void validateTeacherLimit(Integer teacherLimit) {
        if (teacherLimit == null || teacherLimit < 1 || teacherLimit > 10) {
            throw invalidParam("每位教师指导队伍上限必须在 1-10 之间");
        }
    }

    private void validateTopicRules(
            TopicMode topicMode,
            TopicAllocationMode allocationMode,
            Integer changeLimit
    ) {
        if (changeLimit == null || changeLimit < 0 || changeLimit > 3) {
            throw invalidParam("赛题更换次数必须在 0-3 之间");
        }
        if (allocationMode == TopicAllocationMode.FIRST_COME
                && topicMode != TopicMode.PRESET_LIMITED) {
            throw invalidParam("FIRST_COME 只能用于 PRESET_LIMITED 赛题模式");
        }
    }

    private ContestDO requireContest(Long contestId) {
        ContestDO contest = contestId == null ? null : contestMapper.selectById(contestId);
        if (contest == null) {
            throw notFound("竞赛不存在或对当前用户不可见");
        }
        return contest;
    }

    private ContestEditionDO requireEdition(Long editionId) {
        ContestEditionDO edition = editionId == null ? null : editionMapper.selectById(editionId);
        if (edition == null) {
            throw notFound("届次不存在或对当前用户不可见");
        }
        return edition;
    }

    private ContestEditionDO requireVisibleEdition(Long editionId) {
        ContestEditionDO edition = requireEdition(editionId);
        if (!authorizationService.canManage()
                && !EditionStatus.fromDatabaseValue(edition.getStatus()).isVisibleToOrdinaryUser()) {
            throw notFound("届次不存在或对当前用户不可见");
        }
        return edition;
    }

    private boolean isReadOnly(ContestEditionDO edition, LocalDateTime now) {
        return EditionStatus.fromDatabaseValue(edition.getStatus()) == EditionStatus.ARCHIVED
                || (edition.getPublishEnd() != null && now.isAfter(edition.getPublishEnd()));
    }

    private void applyEditionSort(
            LambdaQueryWrapper<ContestEditionDO> wrapper,
            String sort
    ) {
        String value = sort == null || sort.isBlank() ? "-created_at" : sort.trim();
        boolean descending = value.startsWith("-");
        String field = descending ? value.substring(1) : value;
        switch (field) {
            case "created_at" -> {
                if (descending) wrapper.orderByDesc(ContestEditionDO::getCreatedAt);
                else wrapper.orderByAsc(ContestEditionDO::getCreatedAt);
            }
            case "edition_name" -> {
                if (descending) wrapper.orderByDesc(ContestEditionDO::getEditionName);
                else wrapper.orderByAsc(ContestEditionDO::getEditionName);
            }
            case "year" -> {
                if (descending) wrapper.orderByDesc(ContestEditionDO::getYear);
                else wrapper.orderByAsc(ContestEditionDO::getYear);
            }
            case "status" -> {
                if (descending) wrapper.orderByDesc(ContestEditionDO::getStatus);
                else wrapper.orderByAsc(ContestEditionDO::getStatus);
            }
            default -> throw invalidParam(
                    "sort 只支持 created_at、edition_name、year、status，前缀 - 表示降序"
            );
        }
    }

    private Map<String, Object> updateFingerprint(Long editionId, EditionUpdateRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("edition_id", String.valueOf(editionId));
        result.put("edition_name", request.getEditionName());
        result.put("register_deadline", request.getRegisterDeadline());
        result.put("work_deadline", request.getWorkDeadline());
        result.put("review_start", request.getReviewStart());
        result.put("publish_start", request.getPublishStart());
        result.put("publish_end", request.getPublishEnd());
        result.put("min_team_size", request.getMinTeamSize());
        result.put("max_team_size", request.getMaxTeamSize());
        result.put("team_size_mode", request.getTeamSizeMode());
        result.put("max_teams_per_teacher", request.getMaxTeamsPerTeacher());
        result.put("topic_mode", request.getTopicMode());
        result.put("topic_review_required", request.getTopicReviewRequired());
        result.put("topic_allocation_mode", request.getTopicAllocationMode());
        result.put("topic_change_limit", request.getTopicChangeLimit());
        result.put("version", request.getVersion());
        return result;
    }

    private Map<String, Object> editionSnapshot(ContestEditionDO edition) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("edition_name", edition.getEditionName());
        result.put("register_deadline", edition.getRegisterDeadline());
        result.put("work_deadline", edition.getWorkDeadline());
        result.put("review_start", edition.getReviewStart());
        result.put("publish_start", edition.getPublishStart());
        result.put("publish_end", edition.getPublishEnd());
        result.put("team_size_mode", edition.getTeamSizeMode());
        result.put("min_team_size", edition.getMinTeamSize());
        result.put("max_team_size", edition.getMaxTeamSize());
        result.put("max_teams_per_teacher", edition.getMaxTeamsPerTeacher());
        result.put("topic_mode", edition.getTopicMode());
        result.put("topic_review_required", edition.getTopicReviewRequired());
        result.put("topic_allocation_mode", edition.getTopicAllocationMode());
        result.put("topic_change_limit", edition.getTopicChangeLimit());
        result.put("version", edition.getVersion());
        return result;
    }

    private void requireAtLeastOneChange(EditionUpdateRequest request) {
        if (request.getEditionName() == null
                && request.getRegisterDeadline() == null
                && request.getWorkDeadline() == null
                && request.getReviewStart() == null
                && request.getPublishStart() == null
                && request.getPublishEnd() == null
                && request.getMinTeamSize() == null
                && request.getMaxTeamSize() == null
                && request.getTeamSizeMode() == null
                && request.getMaxTeamsPerTeacher() == null
                && request.getTopicMode() == null
                && request.getTopicReviewRequired() == null
                && request.getTopicAllocationMode() == null
                && request.getTopicChangeLimit() == null) {
            throw invalidParam("至少提交一个需要修改的届次字段");
        }
    }

    private int pageNumber(Integer value) {
        int page = value == null ? 1 : value;
        if (page < 1) {
            throw invalidParam("page 必须大于等于 1");
        }
        return page;
    }

    private int pageSize(Integer value) {
        int size = value == null ? 20 : value;
        if (size < 1 || size > 100) {
            throw invalidParam("size 必须在 1 到 100 之间");
        }
        return size;
    }

    private String requiredText(String value, String field, int maxLength) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > maxLength) {
            throw invalidParam(field + " 长度必须为 1-" + maxLength);
        }
        return normalized;
    }

    private LocalDateTime toBusinessTime(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(SHANGHAI_OFFSET).toLocalDateTime();
    }

    private EditionStatus parseStatus(String value) {
        try {
            return EditionStatus.parse(value);
        } catch (IllegalArgumentException exception) {
            throw invalidParam(exception.getMessage());
        }
    }

    private TeamSizeMode parseTeamSizeMode(String value) {
        try {
            return TeamSizeMode.parse(value);
        } catch (IllegalArgumentException exception) {
            throw invalidParam(exception.getMessage());
        }
    }

    private TopicMode parseTopicMode(String value) {
        try {
            return TopicMode.parse(value);
        } catch (IllegalArgumentException exception) {
            throw invalidParam(exception.getMessage());
        }
    }

    private TopicMode parseTopicModeNullable(String value) {
        try {
            return TopicMode.parseNullable(value);
        } catch (IllegalArgumentException exception) {
            throw invalidParam(exception.getMessage());
        }
    }

    private TopicAllocationMode parseAllocationMode(String value) {
        try {
            return TopicAllocationMode.parse(value);
        } catch (IllegalArgumentException exception) {
            throw invalidParam(exception.getMessage());
        }
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

    private BizException duplicateEditionName() {
        return new BizException(
                ErrorCode.DUPLICATE_RELATION.getCode(),
                "同一竞赛下届次名称已存在"
        );
    }

    private BizException versionConflict() {
        return new BizException(
                ErrorCode.BUSINESS_CONFLICT.getCode(),
                "数据已被他人修改，请刷新后重试"
        );
    }

    private BizException invalidState(String message) {
        return new BizException(ErrorCode.INVALID_STATE_TRANSITION.getCode(), message);
    }

    private BizException invalidParam(String message) {
        return new BizException(ErrorCode.INVALID_PARAM.getCode(), message);
    }

    private BizException notFound(String message) {
        return new BizException(ErrorCode.RESOURCE_NOT_FOUND.getCode(), message);
    }
}
