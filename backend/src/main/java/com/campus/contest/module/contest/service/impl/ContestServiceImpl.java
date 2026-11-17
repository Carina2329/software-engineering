package com.campus.contest.module.contest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyScope;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.contest.dto.ContestCreateRequest;
import com.campus.contest.module.contest.dto.ContestPageQuery;
import com.campus.contest.module.contest.dto.ContestUpdateRequest;
import com.campus.contest.module.contest.entity.ContestDO;
import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.enums.ContestLevel;
import com.campus.contest.module.contest.enums.EditionStatus;
import com.campus.contest.module.contest.mapper.ContestEditionMapper;
import com.campus.contest.module.contest.mapper.ContestMapper;
import com.campus.contest.module.contest.service.ContestAuditService;
import com.campus.contest.module.contest.service.ContestAuthorizationService;
import com.campus.contest.module.contest.service.ContestService;
import com.campus.contest.module.contest.vo.ContestCreateVO;
import com.campus.contest.module.contest.vo.ContestDetailVO;
import com.campus.contest.module.contest.vo.ContestItemVO;
import com.campus.contest.module.contest.vo.ContestPageVO;
import com.campus.contest.module.contest.vo.ContestVersionVO;
import com.campus.contest.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ContestServiceImpl implements ContestService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final ContestMapper contestMapper;
    private final ContestEditionMapper editionMapper;
    private final ContestAuthorizationService authorizationService;
    private final IdempotencyService idempotencyService;
    private final CurrentUserProvider currentUserProvider;
    private final ContestAuditService auditService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public ContestPageVO page(ContestPageQuery query) {
        ContestPageQuery effective = query == null ? new ContestPageQuery() : query;
        int page = pageNumber(effective.getPage());
        int size = pageSize(effective.getSize());
        ContestLevel level = effective.getLevel() == null || effective.getLevel().isBlank()
                ? null
                : parseLevel(effective.getLevel());
        String keyword = trimToNull(effective.getKeyword());

        LambdaQueryWrapper<ContestDO> wrapper = Wrappers.lambdaQuery();
        if (level != null) {
            wrapper.eq(ContestDO::getLevel, level.name());
        }
        if (keyword != null) {
            wrapper.and(nested -> nested.like(ContestDO::getContestName, keyword)
                    .or()
                    .like(ContestDO::getOrganizer, keyword));
        }
        applyContestSort(wrapper, effective.getSort());
        if (!authorizationService.canManage()) {
            wrapper.apply("""
                    EXISTS (
                      SELECT 1 FROM contest_edition ce
                      WHERE ce.contest_id = contest.id
                        AND ce.deleted = 0
                        AND ce.status <> 0
                    )
                    """);
        }

        IPage<ContestDO> result = contestMapper.selectPage(new Page<>(page, size), wrapper);
        List<ContestItemVO> items = result.getRecords().stream().map(ContestItemVO::from).toList();
        return new ContestPageVO(items, result.getTotal(), page, size,
                (long) page * size < result.getTotal());
    }

    @Override
    public ContestDetailVO get(Long contestId) {
        ContestDO contest = requireContest(contestId);
        LambdaQueryWrapper<ContestEditionDO> wrapper = Wrappers.<ContestEditionDO>lambdaQuery()
                .eq(ContestEditionDO::getContestId, contestId)
                .orderByDesc(ContestEditionDO::getYear)
                .orderByDesc(ContestEditionDO::getId);
        if (!authorizationService.canManage()) {
            wrapper.ne(ContestEditionDO::getStatus, EditionStatus.DRAFT.getDatabaseValue());
        }
        List<String> editions = editionMapper.selectList(wrapper).stream()
                .map(ContestEditionDO::getEditionName)
                .toList();
        if (!authorizationService.canManage() && editions.isEmpty()) {
            throw notFound("竞赛不存在或对当前用户不可见");
        }
        return ContestDetailVO.from(contest, editions);
    }

    @Override
    public ContestCreateVO create(ContestCreateRequest request, String idempotencyKey) {
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        String contestName = requiredText(request.getContestName(), "contest_name", 128);
        String level = parseLevel(request.getLevel()).name();
        String organizer = optionalText(request.getOrganizer(), "organizer", 128);
        String intro = optionalText(request.getIntro(), "intro", 1000);
        requireIdempotencyKey(idempotencyKey);
        Long operatorId = currentUserProvider.requireUserId();

        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("contest_name", contestName);
        fingerprint.put("level", level);
        fingerprint.put("organizer", organizer);
        fingerprint.put("intro", intro);

        return executeIdempotently(
                operatorId,
                "CONTEST_CREATE",
                idempotencyKey,
                fingerprint,
                ContestCreateVO.class,
                () -> doCreate(contestName, level, organizer, intro, operatorId)
        );
    }

    @Override
    public ContestVersionVO update(
            Long contestId,
            ContestUpdateRequest request,
            String idempotencyKey
    ) {
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        requireVersion(request.getVersion());
        String contestName = request.getContestName() == null
                ? null
                : requiredText(request.getContestName(), "contest_name", 128);
        String level = request.getLevel() == null ? null : parseLevel(request.getLevel()).name();
        String organizer = request.getOrganizer() == null
                ? null
                : optionalText(request.getOrganizer(), "organizer", 128);
        String intro = request.getIntro() == null
                ? null
                : optionalText(request.getIntro(), "intro", 1000);
        if (contestName == null && level == null
                && request.getOrganizer() == null && request.getIntro() == null) {
            throw invalidParam("至少提交一个需要修改的竞赛字段");
        }
        requireIdempotencyKey(idempotencyKey);
        Long operatorId = currentUserProvider.requireUserId();

        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("contest_id", String.valueOf(contestId));
        fingerprint.put("contest_name", contestName);
        fingerprint.put("level", level);
        fingerprint.put("organizer", organizer);
        fingerprint.put("intro", intro);
        fingerprint.put("version", request.getVersion());

        return executeIdempotently(
                operatorId,
                "CONTEST_UPDATE",
                idempotencyKey,
                fingerprint,
                ContestVersionVO.class,
                () -> doUpdate(contestId, contestName, level, organizer, intro,
                        request.getVersion(), operatorId)
        );
    }

    private ContestCreateVO doCreate(
            String contestName,
            String level,
            String organizer,
            String intro,
            Long operatorId
    ) {
        return transactionTemplate.execute(status -> {
            ContestDO entity = new ContestDO();
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            entity.setContestName(contestName);
            entity.setLevel(level);
            entity.setOrganizer(organizer);
            entity.setIntro(intro);
            entity.setCreatedAt(now);
            entity.setCreatedBy(operatorId);
            entity.setUpdatedAt(now);
            entity.setUpdatedBy(operatorId);
            entity.setDeleted(0);
            entity.setVersion(0);
            contestMapper.insert(entity);
            auditService.record(operatorId, "CONTEST_CREATE", Map.of(
                    "contest_id", String.valueOf(entity.getId()),
                    "contest_name", contestName,
                    "level", level
            ));
            return new ContestCreateVO(String.valueOf(entity.getId()), contestName);
        });
    }

    private ContestVersionVO doUpdate(
            Long contestId,
            String contestName,
            String level,
            String organizer,
            String intro,
            Integer version,
            Long operatorId
    ) {
        return transactionTemplate.execute(status -> {
            ContestDO existing = requireContest(contestId);
            if (!Objects.equals(existing.getVersion(), version)) {
                throw versionConflict();
            }
            Map<String, Object> before = snapshot(existing);

            ContestDO update = new ContestDO();
            update.setId(contestId);
            update.setContestName(contestName);
            update.setLevel(level);
            update.setOrganizer(organizer);
            update.setIntro(intro);
            update.setUpdatedAt(LocalDateTime.now(BUSINESS_ZONE));
            update.setUpdatedBy(operatorId);
            update.setVersion(version);
            if (contestMapper.updateById(update) != 1) {
                throw versionConflict();
            }

            Map<String, Object> after = new LinkedHashMap<>();
            after.put("contest_id", String.valueOf(contestId));
            after.put("contest_name", contestName == null ? existing.getContestName() : contestName);
            after.put("level", level == null ? existing.getLevel() : level);
            after.put("organizer", requestOrExisting(organizer, existing.getOrganizer()));
            after.put("intro", requestOrExisting(intro, existing.getIntro()));
            after.put("version", version + 1);
            auditService.record(operatorId, "CONTEST_UPDATE",
                    Map.of("before", before, "after", after));
            return new ContestVersionVO(String.valueOf(contestId), version + 1);
        });
    }

    private ContestDO requireContest(Long contestId) {
        ContestDO contest = contestId == null ? null : contestMapper.selectById(contestId);
        if (contest == null) {
            throw notFound("竞赛不存在或对当前用户不可见");
        }
        return contest;
    }

    private void applyContestSort(LambdaQueryWrapper<ContestDO> wrapper, String sort) {
        String value = sort == null || sort.isBlank() ? "-created_at" : sort.trim();
        boolean descending = value.startsWith("-");
        String field = descending ? value.substring(1) : value;
        switch (field) {
            case "created_at" -> {
                if (descending) wrapper.orderByDesc(ContestDO::getCreatedAt);
                else wrapper.orderByAsc(ContestDO::getCreatedAt);
            }
            case "contest_name" -> {
                if (descending) wrapper.orderByDesc(ContestDO::getContestName);
                else wrapper.orderByAsc(ContestDO::getContestName);
            }
            case "level" -> {
                if (descending) wrapper.orderByDesc(ContestDO::getLevel);
                else wrapper.orderByAsc(ContestDO::getLevel);
            }
            default -> throw invalidParam("sort 只支持 created_at、contest_name、level，前缀 - 表示降序");
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

    private ContestLevel parseLevel(String value) {
        try {
            return ContestLevel.parse(value);
        } catch (IllegalArgumentException exception) {
            throw invalidParam(exception.getMessage());
        }
    }

    private String requiredText(String value, String field, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null || normalized.length() > maxLength) {
            throw invalidParam(field + " 长度必须为 1-" + maxLength);
        }
        return normalized;
    }

    private String optionalText(String value, String field, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw invalidParam(field + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String requestOrExisting(String requestValue, String existingValue) {
        return requestValue == null ? existingValue : requestValue;
    }

    private Map<String, Object> snapshot(ContestDO contest) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contest_name", contest.getContestName());
        result.put("level", contest.getLevel());
        result.put("organizer", contest.getOrganizer());
        result.put("intro", contest.getIntro());
        result.put("version", contest.getVersion());
        return result;
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
            java.util.function.Supplier<T> action
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

    private BizException notFound(String message) {
        return new BizException(ErrorCode.RESOURCE_NOT_FOUND.getCode(), message);
    }

    private BizException invalidParam(String message) {
        return new BizException(ErrorCode.INVALID_PARAM.getCode(), message);
    }

    private BizException versionConflict() {
        return new BizException(
                ErrorCode.BUSINESS_CONFLICT.getCode(),
                "数据已被他人修改，请刷新后重试"
        );
    }
}
