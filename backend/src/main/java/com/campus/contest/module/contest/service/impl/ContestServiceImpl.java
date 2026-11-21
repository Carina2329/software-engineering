package com.campus.contest.module.contest.service.impl;

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
import com.campus.contest.module.contest.mapper.ContestAccessCriteria;
import com.campus.contest.module.contest.mapper.ContestAccessMapper;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class ContestServiceImpl implements ContestService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String SERIES_CODE_PATTERN = "^[A-Z0-9][A-Z0-9_-]{1,31}$";

    private final ContestMapper contestMapper;
    private final ContestAccessMapper accessMapper;
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
        ContestAccessCriteria criteria = authorizationService.currentAccessCriteria();
        criteria.setKeyword(trimToNull(effective.getKeyword()));
        criteria.setSeriesLevel(effective.getLevel() == null || effective.getLevel().isBlank()
                ? null
                : parseLevel(effective.getLevel()).name());
        applyContestSort(criteria, effective.getSort());

        IPage<ContestDO> result = accessMapper.selectContestPage(new Page<>(page, size), criteria);
        List<ContestItemVO> items = result.getRecords().stream().map(ContestItemVO::from).toList();
        return new ContestPageVO(items, result.getTotal(), page, size,
                (long) page * size < result.getTotal());
    }

    @Override
    public ContestDetailVO get(Long contestId) {
        ContestAccessCriteria criteria = authorizationService.currentAccessCriteria();
        criteria.setContestId(contestId);
        ContestDO contest = accessMapper.selectContestById(criteria);
        if (contest == null) {
            throw notFound("竞赛不存在或对当前用户不可见");
        }
        ContestAccessCriteria editionsCriteria = authorizationService.currentAccessCriteria();
        editionsCriteria.setContestId(contestId);
        editionsCriteria.setSortColumn("e.year");
        editionsCriteria.setSortDirection("DESC");
        List<String> editions = accessMapper.selectEditionList(editionsCriteria).stream()
                .map(ContestEditionDO::getEditionName)
                .toList();
        return ContestDetailVO.from(contest, editions);
    }

    @Override
    public ContestCreateVO create(ContestCreateRequest request, String idempotencyKey) {
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        authorizationService.requirePlatformAdmin();
        String contestName = requiredText(request.getContestName(), "contest_name", 128);
        String seriesCode = normalizeSeriesCode(request.getSeriesCode());
        String level = request.getLevel() == null || request.getLevel().isBlank()
                ? ContestLevel.SCHOOL.name()
                : parseLevel(request.getLevel()).name();
        String organizer = optionalText(request.getOrganizer(), "organizer", 128);
        String intro = optionalText(request.getIntro(), "intro", 1000);
        requireIdempotencyKey(idempotencyKey);
        Long operatorId = currentUserProvider.requireUserId();

        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("contest_name", contestName);
        fingerprint.put("series_code", seriesCode);
        fingerprint.put("level", level);
        fingerprint.put("organizer", organizer);
        fingerprint.put("intro", intro);

        return executeIdempotently(
                operatorId, "CONTEST_CREATE", idempotencyKey, fingerprint,
                ContestCreateVO.class,
                () -> doCreate(contestName, seriesCode, level, organizer, intro, operatorId)
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
        authorizationService.requirePlatformAdmin();
        String contestName = request.getContestName() == null
                ? null
                : requiredText(request.getContestName(), "contest_name", 128);
        String seriesCode = request.getSeriesCode() == null
                ? null
                : normalizeSeriesCode(request.getSeriesCode());
        String level = request.getLevel() == null ? null : parseLevel(request.getLevel()).name();
        String organizer = request.getOrganizer() == null
                ? null
                : optionalText(request.getOrganizer(), "organizer", 128);
        String intro = request.getIntro() == null
                ? null
                : optionalText(request.getIntro(), "intro", 1000);
        if (contestName == null && seriesCode == null && level == null
                && request.getOrganizer() == null && request.getIntro() == null) {
            throw invalidParam("至少提交一个需要修改的竞赛字段");
        }
        String optionalKey = trimToNull(idempotencyKey);
        Long operatorId = currentUserProvider.requireUserId();

        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("contest_id", String.valueOf(contestId));
        fingerprint.put("contest_name", contestName);
        fingerprint.put("series_code", seriesCode);
        fingerprint.put("level", level);
        fingerprint.put("organizer", organizer);
        fingerprint.put("intro", intro);
        fingerprint.put("version", request.getVersion());

        return executeOptionallyIdempotently(
                operatorId, "CONTEST_UPDATE", optionalKey, fingerprint,
                ContestVersionVO.class,
                () -> doUpdate(contestId, contestName, seriesCode, level, organizer,
                        intro, request.getVersion(), operatorId)
        );
    }

    private ContestCreateVO doCreate(
            String contestName,
            String seriesCode,
            String level,
            String organizer,
            String intro,
            Long operatorId
    ) {
        return transactionTemplate.execute(status -> {
            if (seriesCodeExists(seriesCode, null)) {
                throw duplicateSeriesCode();
            }
            ContestDO entity = new ContestDO();
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            entity.setContestName(contestName);
            entity.setSeriesCode(seriesCode);
            entity.setLevel(level);
            entity.setOrganizer(organizer);
            entity.setIntro(intro);
            entity.setCreatedAt(now);
            entity.setCreatedBy(operatorId);
            entity.setUpdatedAt(now);
            entity.setUpdatedBy(operatorId);
            entity.setDeleted(0);
            entity.setVersion(0);
            try {
                contestMapper.insert(entity);
            } catch (DuplicateKeyException exception) {
                throw duplicateSeriesCode();
            }
            auditService.record(operatorId, "CONTEST_CREATE", Map.of(
                    "contest_id", String.valueOf(entity.getId()),
                    "contest_name", contestName,
                    "series_code", seriesCode
            ));
            return new ContestCreateVO(String.valueOf(entity.getId()), contestName);
        });
    }

    private ContestVersionVO doUpdate(
            Long contestId,
            String contestName,
            String seriesCode,
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
            if (seriesCode != null && seriesCodeExists(seriesCode, contestId)) {
                throw duplicateSeriesCode();
            }
            Map<String, Object> before = snapshot(existing);

            ContestDO update = new ContestDO();
            update.setId(contestId);
            update.setContestName(contestName);
            update.setSeriesCode(seriesCode);
            update.setLevel(level);
            update.setOrganizer(organizer);
            update.setIntro(intro);
            update.setUpdatedAt(LocalDateTime.now(BUSINESS_ZONE));
            update.setUpdatedBy(operatorId);
            update.setVersion(version);
            try {
                if (contestMapper.updateById(update) != 1) {
                    throw versionConflict();
                }
            } catch (DuplicateKeyException exception) {
                throw duplicateSeriesCode();
            }

            Map<String, Object> after = new LinkedHashMap<>();
            after.put("contest_id", String.valueOf(contestId));
            after.put("contest_name", valueOrExisting(contestName, existing.getContestName()));
            after.put("series_code", valueOrExisting(seriesCode, existing.getSeriesCode()));
            after.put("level", valueOrExisting(level, existing.getLevel()));
            after.put("organizer", valueOrExisting(organizer, existing.getOrganizer()));
            after.put("intro", valueOrExisting(intro, existing.getIntro()));
            after.put("version", version + 1);
            auditService.record(operatorId, "CONTEST_UPDATE",
                    Map.of("before", before, "after", after));
            return new ContestVersionVO(String.valueOf(contestId), version + 1);
        });
    }

    private ContestDO requireContest(Long contestId) {
        ContestDO contest = contestId == null ? null : contestMapper.selectById(contestId);
        if (contest == null) {
            throw notFound("竞赛系列不存在");
        }
        return contest;
    }

    private boolean seriesCodeExists(String seriesCode, Long excludedId) {
        var wrapper = Wrappers.<ContestDO>lambdaQuery()
                .eq(ContestDO::getSeriesCode, seriesCode);
        if (excludedId != null) {
            wrapper.ne(ContestDO::getId, excludedId);
        }
        return contestMapper.selectCount(wrapper) > 0;
    }

    private void applyContestSort(ContestAccessCriteria criteria, String sort) {
        String value = sort == null || sort.isBlank() ? "-created_at" : sort.trim();
        boolean descending = value.startsWith("-");
        String field = descending ? value.substring(1) : value;
        criteria.setSortDirection(descending ? "DESC" : "ASC");
        criteria.setSortColumn(switch (field) {
            case "created_at" -> "c.created_at";
            case "contest_name" -> "c.contest_name";
            case "level" -> "c.level";
            default -> throw invalidParam(
                    "sort 只支持 created_at、contest_name、level，前缀 - 表示降序"
            );
        });
    }

    private String normalizeSeriesCode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!normalized.matches(SERIES_CODE_PATTERN)) {
            throw invalidParam("series_code 必须为 2-32 位大写字母、数字、下划线或短横线");
        }
        return normalized;
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

    private String valueOrExisting(String requestValue, String existingValue) {
        return requestValue == null ? existingValue : requestValue;
    }

    private Map<String, Object> snapshot(ContestDO contest) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contest_name", contest.getContestName());
        result.put("series_code", contest.getSeriesCode());
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

    private BizException duplicateSeriesCode() {
        return new BizException(
                ErrorCode.DUPLICATE_RELATION.getCode(),
                "竞赛系列编码已存在"
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
