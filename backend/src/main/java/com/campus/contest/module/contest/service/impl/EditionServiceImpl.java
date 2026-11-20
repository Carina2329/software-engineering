package com.campus.contest.module.contest.service.impl;

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
import com.campus.contest.module.contest.enums.ContestLevel;
import com.campus.contest.module.contest.enums.EditionStatus;
import com.campus.contest.module.contest.enums.EditionVisibility;
import com.campus.contest.module.contest.enums.EntryMode;
import com.campus.contest.module.contest.enums.TeamSizeMode;
import com.campus.contest.module.contest.enums.TopicAllocationMode;
import com.campus.contest.module.contest.enums.TopicMode;
import com.campus.contest.module.contest.mapper.ContestAccessCriteria;
import com.campus.contest.module.contest.mapper.ContestAccessMapper;
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
import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.organization.model.EffectiveScope;
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
    private static final String ORG_SCHOOL = "SCHOOL";
    private static final String ORG_PROVINCE = "PROVINCE_ORGANIZER";
    private static final String ORG_NATIONAL = "NATIONAL_ORGANIZER";

    private final ContestMapper contestMapper;
    private final ContestEditionMapper editionMapper;
    private final ContestAccessMapper accessMapper;
    private final ContestTeamMapper teamMapper;
    private final OrganizationMapper organizationMapper;
    private final ContestAuthorizationService authorizationService;
    private final IdempotencyService idempotencyService;
    private final CurrentUserProvider currentUserProvider;
    private final ContestAuditService auditService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public EditionPageVO page(Long contestId, EditionPageQuery query) {
        EditionPageQuery effective = query == null ? new EditionPageQuery() : query;
        int page = pageNumber(effective.getPage());
        int size = pageSize(effective.getSize());
        ContestAccessCriteria criteria = authorizationService.currentAccessCriteria();
        criteria.setContestId(contestId);
        criteria.setYear(validateYear(effective.getYear()));
        criteria.setStatus(effective.getStatus() == null || effective.getStatus().isBlank()
                ? null
                : parseStatus(effective.getStatus()).getDatabaseValue());
        criteria.setLevel(effective.getLevel() == null || effective.getLevel().isBlank()
                ? null
                : parseLevel(effective.getLevel()).name());
        criteria.setOwnerOrgId(parseOptionalId(effective.getOwnerOrgId(), "owner_org_id"));
        criteria.setProvinceCode(trimToNull(effective.getProvinceCode()));
        criteria.setSchoolId(parseOptionalId(effective.getSchoolId(), "school_id"));
        criteria.setVisibility(effective.getVisibility() == null || effective.getVisibility().isBlank()
                ? null
                : parseVisibility(effective.getVisibility()).name());
        applyEditionSort(criteria, effective.getSort());

        ContestAccessCriteria visibleContestCriteria = authorizationService.currentAccessCriteria();
        visibleContestCriteria.setContestId(contestId);
        if (accessMapper.selectContestById(visibleContestCriteria) == null) {
            throw notFound("竞赛不存在或对当前用户不可见");
        }

        IPage<ContestEditionDO> result =
                accessMapper.selectEditionPage(new Page<>(page, size), criteria);
        Map<Long, String> ownerNames = organizationNames(result.getRecords());
        List<EditionItemVO> items = result.getRecords().stream()
                .map(edition -> EditionItemVO.from(
                        edition,
                        ownerNames.get(edition.getOwnerOrgId())
                ))
                .toList();
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
        ContestLevel level = parseLevel(request.getLevel());
        String editionName = requiredText(request.getEditionName(), "edition_name", 128);
        if (request.getYear() == null || request.getYear() < 2000 || request.getYear() > 2100) {
            throw invalidParam("year 必须在 2000 到 2100 之间");
        }
        Long schoolId = parseOptionalId(request.getSchoolId(), "school_id");
        String provinceCode = trimToNull(request.getProvinceCode());
        Long ownerOrgId = resolveOwnerOrg(
                parseOptionalId(request.getOwnerOrgId(), "owner_org_id"),
                level,
                schoolId,
                provinceCode
        );
        OrganizationDO owner = authorizationService.requireActiveOrganization(ownerOrgId);
        authorizationService.requireCreateScope(level, ownerOrgId, provinceCode);
        validateScope(
                level,
                owner,
                schoolId,
                provinceCode,
                parseVisibilityOrDefault(request.getVisibility(), EditionVisibility.DRAFT)
        );

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
        fingerprint.put("owner_org_id", String.valueOf(ownerOrgId));
        fingerprint.put("edition_name", editionName);
        fingerprint.put("year", request.getYear());
        fingerprint.put("level", level.name());
        fingerprint.put("school_id", schoolId);
        fingerprint.put("province_code", provinceCode);
        fingerprint.put("visibility", parseVisibilityOrDefault(
                request.getVisibility(), EditionVisibility.DRAFT).name());
        fingerprint.put("register_deadline", registerDeadline);
        fingerprint.put("work_deadline", workDeadline);
        fingerprint.put("review_start", reviewStart);
        fingerprint.put("publish_start", publishStart);
        fingerprint.put("publish_end", publishEnd);
        fingerprint.put("min_team_size", request.getMinTeamSize());
        fingerprint.put("max_team_size", request.getMaxTeamSize());
        fingerprint.put("team_size_mode", teamSizeMode.name());
        fingerprint.put("max_teams_per_teacher", teacherLimit);
        fingerprint.put("topic_mode", topicMode == null ? null : topicMode.name());
        fingerprint.put("topic_review_required", request.getTopicReviewRequired());
        fingerprint.put("topic_allocation_mode", allocationMode.name());
        fingerprint.put("topic_change_limit", changeLimit);
        fingerprint.put("entry_mode", parseEntryModeOrDefault(
                request.getEntryMode(), EntryMode.OPEN).name());
        fingerprint.put("cross_school_team_allowed",
                Boolean.TRUE.equals(request.getCrossSchoolTeamAllowed()));

        return executeIdempotently(
                operatorId, "EDITION_CREATE", idempotencyKey, fingerprint,
                EditionCreateVO.class,
                () -> doCreate(
                        contestId, ownerOrgId, editionName, request, level, schoolId,
                        provinceCode, teamSizeMode, teacherLimit, topicMode,
                        allocationMode, changeLimit, registerDeadline, workDeadline,
                        reviewStart, publishStart, publishEnd, operatorId
                )
        );
    }

    @Override
    public EditionDetailVO get(Long editionId) {
        ContestAccessCriteria criteria = authorizationService.currentAccessCriteria();
        criteria.setEditionId(editionId);
        ContestEditionDO edition = accessMapper.selectEditionById(criteria);
        if (edition == null) {
            throw notFound("届次不存在或对当前用户不可见");
        }
        OrganizationDO owner = organizationMapper.selectById(edition.getOwnerOrgId());
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
        return EditionDetailVO.from(
                edition,
                registrationOpen,
                submissionOpen,
                readOnly,
                owner == null ? null : owner.getOrgName()
        );
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
        ContestEditionDO managedEdition = requireEdition(editionId);
        authorizationService.requireContestEventManageable(managedEdition);
        String optionalKey = trimToNull(idempotencyKey);
        Long operatorId = currentUserProvider.requireUserId();
        Map<String, Object> fingerprint = updateFingerprint(editionId, request);
        return executeOptionallyIdempotently(
                operatorId, "EDITION_UPDATE", optionalKey, fingerprint,
                EditionVersionVO.class,
                () -> doUpdate(editionId, request, operatorId)
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
        ContestEditionDO managedEdition = requireEdition(editionId);
        authorizationService.requireContestEventManageable(managedEdition);
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
            Long ownerOrgId,
            String editionName,
            EditionCreateRequest request,
            ContestLevel level,
            Long schoolId,
            String provinceCode,
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
            ensureEditionNameAvailable(
                    ownerOrgId, request.getYear(), editionName, null
            );
            ContestEditionDO entity = new ContestEditionDO();
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            entity.setContestId(contestId);
            entity.setOwnerOrgId(ownerOrgId);
            entity.setEditionName(editionName);
            entity.setYear(request.getYear());
            entity.setLevel(level.name());
            entity.setSchoolId(schoolId);
            entity.setProvinceCode(provinceCode);
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
            entity.setVisibility(parseVisibilityOrDefault(
                    request.getVisibility(), EditionVisibility.DRAFT).name());
            entity.setEntryMode(parseEntryModeOrDefault(
                    request.getEntryMode(), EntryMode.OPEN).name());
            entity.setCrossSchoolTeamAllowed(
                    Boolean.TRUE.equals(request.getCrossSchoolTeamAllowed()) ? 1 : 0
            );
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
                    "owner_org_id", String.valueOf(ownerOrgId),
                    "level", level.name()
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

            EffectiveScope scope = authorizationService.currentScope();
            Long requestedOwner = parseOptionalId(request.getOwnerOrgId(), "owner_org_id");
            if (requestedOwner != null
                    && !requestedOwner.equals(existing.getOwnerOrgId())
                    && !scope.global()) {
                throw dataForbidden("只有平台管理员可以变更主办组织");
            }
            Long ownerOrgId = requestedOwner == null ? existing.getOwnerOrgId() : requestedOwner;
            OrganizationDO owner = authorizationService.requireActiveOrganization(ownerOrgId);

            ContestLevel level = request.getLevel() == null
                    ? parseLevel(existing.getLevel())
                    : parseLevel(request.getLevel());
            Long schoolId = request.getSchoolId() == null
                    ? existing.getSchoolId()
                    : parseOptionalId(request.getSchoolId(), "school_id");
            String provinceCode = request.getProvinceCode() == null
                    ? existing.getProvinceCode()
                    : trimToNull(request.getProvinceCode());
            if (request.getLevel() != null) {
                if (level == ContestLevel.NATIONAL) {
                    schoolId = null;
                    provinceCode = null;
                } else if (level == ContestLevel.SCHOOL) {
                    provinceCode = null;
                } else {
                    schoolId = null;
                }
            }
            EditionVisibility visibility = request.getVisibility() == null
                    ? parseVisibility(existing.getVisibility())
                    : parseVisibility(request.getVisibility());
            boolean scopeChanged = !Objects.equals(existing.getOwnerOrgId(), ownerOrgId)
                    || !Objects.equals(existing.getLevel(), level.name())
                    || !Objects.equals(existing.getSchoolId(), schoolId)
                    || !Objects.equals(existing.getProvinceCode(), provinceCode);
            if (scopeChanged) {
                authorizationService.requireCreateScope(level, ownerOrgId, provinceCode);
            }
            validateScope(level, owner, schoolId, provinceCode, visibility);

            String editionName = request.getEditionName() == null
                    ? existing.getEditionName()
                    : requiredText(request.getEditionName(), "edition_name", 128);
            TeamSizeMode mode = request.getTeamSizeMode() == null
                    ? parseTeamSizeMode(existing.getTeamSizeMode())
                    : parseTeamSizeMode(request.getTeamSizeMode());
            TopicMode topicMode = request.getTopicMode() == null
                    ? parseTopicModeNullable(existing.getTopicMode())
                    : parseTopicMode(request.getTopicMode());
            TopicAllocationMode allocationMode = request.getTopicAllocationMode() == null
                    ? parseAllocationMode(existing.getTopicAllocationMode())
                    : parseAllocationMode(request.getTopicAllocationMode());
            int minSize = request.getMinTeamSize() == null
                    ? existing.getMinTeamSize()
                    : request.getMinTeamSize();
            int maxSize = request.getMaxTeamSize() == null
                    ? existing.getMaxTeamSize()
                    : request.getMaxTeamSize();
            int teacherLimit = request.getMaxTeamsPerTeacher() == null
                    ? existing.getMaxTeamsPerTeacher()
                    : request.getMaxTeamsPerTeacher();
            int changeLimit = request.getTopicChangeLimit() == null
                    ? existing.getTopicChangeLimit()
                    : request.getTopicChangeLimit();
            validateTeamSizes(mode, minSize, maxSize);
            validateTeacherLimit(teacherLimit);
            validateTopicRules(topicMode, allocationMode, changeLimit);

            long teamCount = teamMapper.countByEditionId(editionId);
            if (teamCount > 0) {
                ensureStructuralFieldsUnchanged(
                        existing, mode, minSize, maxSize, topicMode, changeLimit, teacherLimit
                );
            }

            LocalDateTime registerDeadline = request.getRegisterDeadline() == null
                    ? existing.getRegisterDeadline()
                    : toBusinessTime(request.getRegisterDeadline());
            LocalDateTime workDeadline = request.getWorkDeadline() == null
                    ? existing.getWorkDeadline()
                    : toBusinessTime(request.getWorkDeadline());
            LocalDateTime reviewStart = request.getReviewStart() == null
                    ? existing.getReviewStart()
                    : toBusinessTime(request.getReviewStart());
            LocalDateTime publishStart = request.getPublishStart() == null
                    ? existing.getPublishStart()
                    : toBusinessTime(request.getPublishStart());
            LocalDateTime publishEnd = request.getPublishEnd() == null
                    ? existing.getPublishEnd()
                    : toBusinessTime(request.getPublishEnd());
            validateTimeOrderWhenComplete(
                    registerDeadline, workDeadline, reviewStart, publishStart, publishEnd
            );
            ensureDeadlinesOnlyExtended(existing, request, now);
            ensureEditionNameAvailable(ownerOrgId, existing.getYear(), editionName, editionId);

            Map<String, Object> before = editionSnapshot(existing);
            ContestEditionDO update = new ContestEditionDO();
            update.setId(editionId);
            update.setOwnerOrgId(requestedOwner);
            update.setEditionName(request.getEditionName() == null ? null : editionName);
            update.setLevel(request.getLevel() == null ? null : level.name());
            update.setSchoolId(request.getSchoolId() == null ? null : schoolId);
            update.setProvinceCode(request.getProvinceCode() == null ? null : provinceCode);
            update.setRegisterDeadline(request.getRegisterDeadline() == null ? null : registerDeadline);
            update.setWorkDeadline(request.getWorkDeadline() == null ? null : workDeadline);
            update.setReviewStart(request.getReviewStart() == null ? null : reviewStart);
            update.setPublishStart(request.getPublishStart() == null ? null : publishStart);
            update.setPublishEnd(request.getPublishEnd() == null ? null : publishEnd);
            update.setMinTeamSize(request.getMinTeamSize());
            update.setMaxTeamSize(request.getMaxTeamSize());
            update.setTeamSizeMode(request.getTeamSizeMode() == null ? null : mode.name());
            update.setMaxTeamsPerTeacher(request.getMaxTeamsPerTeacher());
            update.setTopicMode(request.getTopicMode() == null ? null : topicMode.name());
            update.setTopicReviewRequired(
                    request.getTopicReviewRequired() == null
                            ? null
                            : (request.getTopicReviewRequired() ? 1 : 0)
            );
            update.setTopicAllocationMode(
                    request.getTopicAllocationMode() == null ? null : allocationMode.name()
            );
            update.setTopicChangeLimit(request.getTopicChangeLimit());
            update.setVisibility(request.getVisibility() == null ? null : visibility.name());
            update.setEntryMode(
                    request.getEntryMode() == null
                            ? null
                            : parseEntryMode(request.getEntryMode()).name()
            );
            update.setCrossSchoolTeamAllowed(
                    request.getCrossSchoolTeamAllowed() == null
                            ? null
                            : (request.getCrossSchoolTeamAllowed() ? 1 : 0)
            );
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
            after.put("owner_org_id", String.valueOf(ownerOrgId));
            after.put("level", level.name());
            after.put("school_id", schoolId);
            after.put("province_code", provinceCode);
            after.put("visibility", visibility.name());
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

            EditionVisibility visibility = parseVisibility(existing.getVisibility());
            if (visibility == EditionVisibility.DRAFT) {
                visibility = defaultPublishedVisibility(existing.getLevel());
            }
            ContestEditionDO update = new ContestEditionDO();
            update.setId(editionId);
            update.setStatus(EditionStatus.REGISTERING.getDatabaseValue());
            update.setVisibility(visibility.name());
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
                    "visibility", visibility.name(),
                    "version", version + 1
            ));
            return new EditionPublishVO(
                    String.valueOf(editionId),
                    EditionStatus.REGISTERING.name()
            );
        });
    }

    private Long resolveOwnerOrg(
            Long requestedOwnerOrgId,
            ContestLevel level,
            Long schoolId,
            String provinceCode
    ) {
        EffectiveScope scope = authorizationService.currentScope();
        if (requestedOwnerOrgId != null) {
            return requestedOwnerOrgId;
        }
        if (level == ContestLevel.SCHOOL && schoolId != null) {
            return schoolId;
        }
        if (!scope.global() && scope.organizationIds().size() == 1) {
            return scope.organizationIds().iterator().next();
        }
        if (level == ContestLevel.PROVINCE && provinceCode != null) {
            List<OrganizationDO> organizers = organizationMapper.selectList(
                    Wrappers.<OrganizationDO>lambdaQuery()
                            .eq(OrganizationDO::getOrgType, ORG_PROVINCE)
                            .eq(OrganizationDO::getProvinceCode, provinceCode)
                            .eq(OrganizationDO::getStatus, 1)
                            .orderByAsc(OrganizationDO::getId)
            );
            if (!organizers.isEmpty()) {
                return organizers.get(0).getId();
            }
        }
        throw invalidParam("owner_org_id 不能为空，且必须位于当前管理员授权范围内");
    }

    private void validateScope(
            ContestLevel level,
            OrganizationDO owner,
            Long schoolId,
            String provinceCode,
            EditionVisibility visibility
    ) {
        switch (level) {
            case SCHOOL -> {
                if (schoolId == null || provinceCode != null || !schoolId.equals(owner.getId())) {
                    throw invalidParam("校级比赛必须绑定所属学校，且主办组织必须是该学校");
                }
                if (!ORG_SCHOOL.equals(owner.getOrgType())) {
                    throw invalidParam("校级比赛主办组织类型必须为 SCHOOL");
                }
            }
            case PROVINCE -> {
                if (schoolId != null || provinceCode == null) {
                    throw invalidParam("省级比赛必须绑定省份，且不能绑定学校");
                }
                if (!ORG_PROVINCE.equals(owner.getOrgType())
                        || !Objects.equals(owner.getProvinceCode(), provinceCode)) {
                    throw invalidParam("省级比赛主办组织必须是同省份的省级主办方");
                }
            }
            case NATIONAL -> {
                if (schoolId != null || provinceCode != null) {
                    throw invalidParam("国家级比赛不能绑定学校或省份范围");
                }
                if (!ORG_NATIONAL.equals(owner.getOrgType())) {
                    throw invalidParam("国家级比赛主办组织类型必须为 NATIONAL_ORGANIZER");
                }
            }
        }
        if (visibility == EditionVisibility.SCHOOL && schoolId == null) {
            throw invalidParam("SCHOOL 可见范围必须指定学校范围");
        }
        if (visibility == EditionVisibility.PROVINCE && provinceCode == null) {
            throw invalidParam("PROVINCE 可见范围必须指定省份范围");
        }
    }

    private EditionVisibility defaultPublishedVisibility(String level) {
        return switch (parseLevel(level)) {
            case SCHOOL -> EditionVisibility.SCHOOL;
            case PROVINCE -> EditionVisibility.PROVINCE;
            case NATIONAL -> EditionVisibility.PUBLIC;
        };
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
        OrganizationDO owner = authorizationService.requireActiveOrganization(edition.getOwnerOrgId());
        validateScope(
                parseLevel(edition.getLevel()),
                owner,
                edition.getSchoolId(),
                edition.getProvinceCode(),
                parseVisibility(edition.getVisibility())
        );
    }

    private void ensureStructuralFieldsUnchanged(
            ContestEditionDO existing,
            TeamSizeMode mode,
            int minSize,
            int maxSize,
            TopicMode topicMode,
            int changeLimit,
            int teacherLimit
    ) {
        if (mode != parseTeamSizeMode(existing.getTeamSizeMode())
                || minSize != existing.getMinTeamSize()
                || maxSize != existing.getMaxTeamSize()
                || topicMode != parseTopicModeNullable(existing.getTopicMode())
                || changeLimit != existing.getTopicChangeLimit()
                || teacherLimit != existing.getMaxTeamsPerTeacher()) {
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
        if (EditionStatus.fromDatabaseValue(existing.getStatus()) == EditionStatus.DRAFT) {
            return;
        }
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
        if (existing != null && requested != null && requested.isBefore(existing)) {
            throw new BizException(
                    ErrorCode.BUSINESS_DEADLINE_PASSED.getCode(),
                    label + "在已发布届次中只能延长，不能提前"
            );
        }
    }

    private void ensureEditionNameAvailable(
            Long ownerOrgId,
            Integer year,
            String editionName,
            Long excludedId
    ) {
        var wrapper = Wrappers.<ContestEditionDO>lambdaQuery()
                .eq(ContestEditionDO::getOwnerOrgId, ownerOrgId)
                .eq(ContestEditionDO::getYear, year)
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
            throw notFound("竞赛系列不存在");
        }
        return contest;
    }

    private ContestEditionDO requireEdition(Long editionId) {
        ContestEditionDO edition = editionId == null ? null : editionMapper.selectById(editionId);
        if (edition == null) {
            throw notFound("届次不存在");
        }
        return edition;
    }

    private boolean isReadOnly(ContestEditionDO edition, LocalDateTime now) {
        EditionStatus status = EditionStatus.fromDatabaseValue(edition.getStatus());
        if (status == EditionStatus.DRAFT) {
            return false;
        }
        return status == EditionStatus.ARCHIVED
                || (edition.getPublishEnd() != null && now.isAfter(edition.getPublishEnd()));
    }

    private void applyEditionSort(ContestAccessCriteria criteria, String sort) {
        String value = sort == null || sort.isBlank() ? "-created_at" : sort.trim();
        boolean descending = value.startsWith("-");
        String field = descending ? value.substring(1) : value;
        criteria.setSortDirection(descending ? "DESC" : "ASC");
        criteria.setSortColumn(switch (field) {
            case "created_at" -> "e.created_at";
            case "edition_name" -> "e.edition_name";
            case "year" -> "e.year";
            case "status" -> "e.status";
            default -> throw invalidParam(
                    "sort 只支持 created_at、edition_name、year、status，前缀 - 表示降序"
            );
        });
    }

    private Map<Long, String> organizationNames(List<ContestEditionDO> editions) {
        List<Long> ids = editions.stream()
                .map(ContestEditionDO::getOwnerOrgId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> result = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        organizationMapper.selectBatchIds(ids)
                .forEach(organization -> result.put(organization.getId(), organization.getOrgName()));
        return result;
    }

    private Map<String, Object> updateFingerprint(Long editionId, EditionUpdateRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("edition_id", String.valueOf(editionId));
        result.put("version", request.getVersion());
        result.put("edition_name", request.getEditionName());
        result.put("owner_org_id", request.getOwnerOrgId());
        result.put("level", request.getLevel());
        result.put("school_id", request.getSchoolId());
        result.put("province_code", request.getProvinceCode());
        result.put("visibility", request.getVisibility());
        result.put("entry_mode", request.getEntryMode());
        result.put("cross_school_team_allowed", request.getCrossSchoolTeamAllowed());
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
        return result;
    }

    private Map<String, Object> editionSnapshot(ContestEditionDO edition) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("owner_org_id", edition.getOwnerOrgId());
        result.put("level", edition.getLevel());
        result.put("school_id", edition.getSchoolId());
        result.put("province_code", edition.getProvinceCode());
        result.put("visibility", edition.getVisibility());
        result.put("entry_mode", edition.getEntryMode());
        result.put("cross_school_team_allowed", edition.getCrossSchoolTeamAllowed());
        result.put("edition_name", edition.getEditionName());
        result.put("register_deadline", edition.getRegisterDeadline());
        result.put("work_deadline", edition.getWorkDeadline());
        result.put("review_start", edition.getReviewStart());
        result.put("publish_start", edition.getPublishStart());
        result.put("publish_end", edition.getPublishEnd());
        result.put("version", edition.getVersion());
        return result;
    }

    private void requireAtLeastOneChange(EditionUpdateRequest request) {
        if (request.getOwnerOrgId() == null
                && request.getLevel() == null
                && request.getSchoolId() == null
                && request.getProvinceCode() == null
                && request.getVisibility() == null
                && request.getEntryMode() == null
                && request.getCrossSchoolTeamAllowed() == null
                && request.getEditionName() == null
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

    private Integer validateYear(Integer year) {
        if (year == null) {
            return null;
        }
        if (year < 2000 || year > 2100) {
            throw invalidParam("year 必须在 2000 到 2100 之间");
        }
        return year;
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
        String normalized = trimToNull(value);
        if (normalized == null || normalized.length() > maxLength) {
            throw invalidParam(field + " 长度必须为 1-" + maxLength);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Long parseOptionalId(String value, String field) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            Long parsed = Long.valueOf(normalized);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalidParam(field + " 必须为正整数");
        }
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

    private ContestLevel parseLevel(String value) {
        try {
            return ContestLevel.parse(value);
        } catch (IllegalArgumentException exception) {
            throw invalidParam(exception.getMessage());
        }
    }

    private EditionVisibility parseVisibility(String value) {
        try {
            return EditionVisibility.parse(value);
        } catch (IllegalArgumentException exception) {
            throw invalidParam(exception.getMessage());
        }
    }

    private EditionVisibility parseVisibilityOrDefault(
            String value,
            EditionVisibility defaultValue
    ) {
        return value == null || value.isBlank() ? defaultValue : parseVisibility(value);
    }

    private EntryMode parseEntryMode(String value) {
        try {
            return EntryMode.parse(value);
        } catch (IllegalArgumentException exception) {
            throw invalidParam(exception.getMessage());
        }
    }

    private EntryMode parseEntryModeOrDefault(String value, EntryMode defaultValue) {
        return value == null || value.isBlank() ? defaultValue : parseEntryMode(value);
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
                scopeOf(operatorId), operation, idempotencyKey, fingerprint,
                IDEMPOTENCY_TTL, resultType, action
        );
    }

    private <T> T executeOptionallyIdempotently(
            Long operatorId,
            String operation,
            String idempotencyKey,
            Object fingerprint,
            Class<T> resultType,
            Supplier<T> action
    ) {
        if (idempotencyKey == null) {
            return action.get();
        }
        if (idempotencyKey.length() > 64) {
            throw invalidParam("Idempotency-Key 长度不能超过 64");
        }
        return executeIdempotently(
                operatorId, operation, idempotencyKey, fingerprint, resultType, action
        );
    }

    private IdempotencyScope scopeOf(Long operatorId) {
        return new IdempotencyScope(
                IdempotencyScope.PrincipalType.USER,
                String.valueOf(operatorId)
        );
    }

    private BizException duplicateEditionName() {
        return new BizException(
                ErrorCode.DUPLICATE_RELATION.getCode(),
                "同一主办组织、同一年度下比赛名称已存在"
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

    private BizException dataForbidden(String message) {
        return new BizException(ErrorCode.DATA_FORBIDDEN.getCode(), message);
    }

    private BizException notFound(String message) {
        return new BizException(ErrorCode.RESOURCE_NOT_FOUND.getCode(), message);
    }
}
