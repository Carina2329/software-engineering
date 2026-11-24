package com.campus.contest.module.team.support.service.impl;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper.QuotaRow;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper.RegistrationRow;
import com.campus.contest.module.team.support.model.SchoolQuotaReservation;
import com.campus.contest.module.team.support.service.SchoolQuotaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SchoolQuotaServiceImpl implements SchoolQuotaService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final RegistrationSupportMapper supportMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public SchoolQuotaReservation occupy(
            Long registrationId,
            Long editionId,
            Long schoolId
    ) {
        requireIds(registrationId, editionId, schoolId);
        RegistrationRow registration = supportMapper.selectRegistrationForUpdate(registrationId);
        if (registration == null) {
            throw notFound("报名轮次不存在");
        }
        if (!Objects.equals(registration.editionId(), editionId)) {
            throw conflict("报名轮次与届次不匹配");
        }

        QuotaRow existingQuota = supportMapper.selectQuota(editionId, schoolId);
        if (Integer.valueOf(1).equals(registration.quotaHeld())) {
            if (existingQuota == null) {
                throw conflict("报名轮次占用的学校名额记录已失效");
            }
            return reservation(existingQuota, schoolId, true);
        }

        QuotaRow quota = supportMapper.selectQuotaForUpdate(editionId, schoolId);
        if (quota == null || quota.maxTeams() == null) {
            updateSnapshot(registrationId, schoolId, quota, quota == null ? 0 : quota.usedTeams(), false);
            return reservation(quota, schoolId, false);
        }
        int used = quota.usedTeams() == null ? 0 : quota.usedTeams();
        if (used >= quota.maxTeams()) {
            throw conflict("队长学校报名名额已满");
        }
        if (supportMapper.incrementQuota(quota.id()) != 1) {
            throw conflict("队长学校报名名额已满");
        }
        int usedAfter = used + 1;
        updateSnapshot(registrationId, schoolId, quota, usedAfter, true);
        return new SchoolQuotaReservation(
                quota.id(),
                schoolId,
                quota.maxTeams(),
                usedAfter,
                true,
                true
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void release(Long registrationId) {
        if (registrationId == null) {
            throw invalid("报名轮次 ID 不能为空");
        }
        RegistrationRow registration = supportMapper.selectRegistrationForUpdate(registrationId);
        if (registration == null) {
            throw notFound("报名轮次不存在");
        }
        if (!Integer.valueOf(1).equals(registration.quotaHeld())) {
            return;
        }
        if (registration.quotaSchoolId() == null) {
            throw conflict("报名轮次缺少名额学校快照");
        }
        QuotaRow quota = supportMapper.selectQuotaForUpdate(
                registration.editionId(),
                registration.quotaSchoolId()
        );
        if (quota == null || supportMapper.decrementQuota(quota.id()) != 1) {
            throw conflict("学校名额占用记录异常，无法安全释放");
        }
        if (supportMapper.clearQuotaHeld(registrationId) != 1) {
            throw conflict("报名轮次名额状态已变化");
        }
    }

    private void updateSnapshot(
            Long registrationId,
            Long schoolId,
            QuotaRow quota,
            int usedTeams,
            boolean held
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("quota_id", quota == null ? null : String.valueOf(quota.id()));
        snapshot.put("school_id", String.valueOf(schoolId));
        snapshot.put("max_teams", quota == null ? null : quota.maxTeams());
        snapshot.put("used_teams", quota == null ? null : usedTeams);
        snapshot.put("limited", quota != null && quota.maxTeams() != null);
        snapshot.put("held", held);
        snapshot.put(
                "captured_at",
                OffsetDateTime.now(clock)
                        .atZoneSameInstant(BUSINESS_ZONE)
                        .toOffsetDateTime()
                        .truncatedTo(ChronoUnit.SECONDS)
                        .toString()
        );
        String json;
        try {
            json = objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new BizException(
                    ErrorCode.INTERNAL_ERROR.getCode(),
                    "学校名额快照序列化失败"
            );
        }
        if (supportMapper.updateQuotaSnapshot(
                registrationId,
                schoolId,
                json,
                held ? 1 : 0
        ) != 1) {
            throw conflict("报名轮次名额状态已变化");
        }
    }

    private SchoolQuotaReservation reservation(
            QuotaRow quota,
            Long schoolId,
            boolean held
    ) {
        return new SchoolQuotaReservation(
                quota == null ? null : quota.id(),
                schoolId,
                quota == null ? null : quota.maxTeams(),
                quota == null ? null : quota.usedTeams(),
                quota != null && quota.maxTeams() != null,
                held
        );
    }

    private void requireIds(Long registrationId, Long editionId, Long schoolId) {
        if (registrationId == null || editionId == null || schoolId == null) {
            throw invalid("报名轮次、届次和名额学校不能为空");
        }
    }

    private BizException invalid(String message) {
        return new BizException(ErrorCode.INVALID_PARAM.getCode(), message);
    }

    private BizException notFound(String message) {
        return new BizException(ErrorCode.RESOURCE_NOT_FOUND.getCode(), message);
    }

    private BizException conflict(String message) {
        return new BizException(ErrorCode.BUSINESS_CONFLICT.getCode(), message);
    }
}
