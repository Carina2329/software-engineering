package com.campus.contest.module.organization.service.impl;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.organization.entity.ContestEditionScopeDO;
import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.ContestEditionScopeMapper;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.organization.mapper.OrganizationScopeMapper;
import com.campus.contest.module.organization.model.EffectiveGrant;
import com.campus.contest.module.organization.model.EffectiveScope;
import com.campus.contest.module.organization.model.ScopeGrantDO;
import com.campus.contest.module.organization.model.ScopeType;
import com.campus.contest.module.organization.service.OrganizationScopeService;
import com.campus.contest.module.system.service.DictionaryQueryService;
import com.campus.contest.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrganizationScopeServiceImpl implements OrganizationScopeService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_SCHOOL_ADMIN = "SCHOOL_ADMIN";
    private static final String ROLE_PROVINCE_ADMIN = "PROVINCE_ADMIN";
    private static final String ROLE_NATIONAL_ADMIN = "NATIONAL_ADMIN";
    private static final String ROLE_EVENT_MANAGER = "EVENT_MANAGER";
    private static final String ROLE_REVIEWER = "REVIEWER";

    private static final String ORG_SCHOOL = "SCHOOL";
    private static final String ORG_NATIONAL = "NATIONAL_ORGANIZER";
    private static final String PROVINCE_DICTIONARY = "CN_PROVINCE";

    private static final int GRANT_ACTIVE = 1;
    private static final int NOT_DELETED = 0;
    private static final int ORGANIZATION_ACTIVE = 1;

    private final OrganizationScopeMapper scopeMapper;
    private final OrganizationMapper organizationMapper;
    private final ContestEditionScopeMapper contestEditionMapper;
    private final DictionaryQueryService dictionaryQueryService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public EffectiveScope loadRequired(Long userId) {
        return resolve(userId).scope();
    }

    @Override
    public List<EffectiveGrant> loadEffectiveGrants(Long userId) {
        return resolve(userId).grants();
    }

    private ScopeResolution resolve(Long userId) {
        if (userId == null) {
            return new ScopeResolution(EffectiveScope.empty(), List.of());
        }

        LocalDateTime now = LocalDateTime.now();
        List<ScopeGrantDO> storedGrants = scopeMapper.selectActiveGrants(userId, now);
        if (storedGrants == null || storedGrants.isEmpty()) {
            return new ScopeResolution(EffectiveScope.empty(), List.of());
        }
        List<ScopeGrantDO> grants = storedGrants.stream()
                .filter(grant -> isEffective(grant, now))
                .toList();
        if (grants.isEmpty()) {
            return new ScopeResolution(EffectiveScope.empty(), List.of());
        }

        Map<Long, OrganizationDO> organizations = loadOrganizations(grants);
        Map<Long, ContestEditionScopeDO> events = loadEvents(grants);

        boolean global = false;
        Set<Long> organizationIds = new LinkedHashSet<>();
        Set<Long> provinceCodes = new LinkedHashSet<>();
        Set<Long> eventIds = new LinkedHashSet<>();
        List<EffectiveGrant> effectiveGrants = new ArrayList<>();

        for (ScopeGrantDO grant : grants) {
            Optional<ScopeType> scopeType = ScopeType.from(grant.getScopeType());
            if (scopeType.isEmpty()) {
                continue;
            }
            String roleCode = normalizeCode(grant.getRoleCode());
            Long scopeId = grant.getScopeId();
            if (roleCode == null || scopeId == null) {
                continue;
            }

            switch (scopeType.get()) {
                case GLOBAL -> {
                    if (ROLE_ADMIN.equals(roleCode)
                            && "ALL".equals(normalizeCode(grant.getDataScope()))
                            && Objects.equals(0L, scopeId)) {
                        global = true;
                        effectiveGrants.add(new EffectiveGrant(
                                ROLE_ADMIN,
                                ScopeType.GLOBAL,
                                scopeId
                        ));
                    }
                }
                case ORGANIZATION -> {
                    OrganizationDO organization = organizations.get(scopeId);
                    if (isAllowedOrganizationGrant(roleCode, organization)) {
                        organizationIds.add(scopeId);
                        effectiveGrants.add(new EffectiveGrant(
                                roleCode,
                                ScopeType.ORGANIZATION,
                                scopeId
                        ));
                    }
                }
                case PROVINCE -> {
                    if (isAllowedProvinceGrant(roleCode, scopeId)) {
                        provinceCodes.add(scopeId);
                        effectiveGrants.add(new EffectiveGrant(
                                roleCode,
                                ScopeType.PROVINCE,
                                scopeId
                        ));
                    }
                }
                case EVENT -> {
                    if (isAllowedEventGrant(roleCode) && events.containsKey(scopeId)) {
                        eventIds.add(scopeId);
                        effectiveGrants.add(new EffectiveGrant(
                                roleCode,
                                ScopeType.EVENT,
                                scopeId
                        ));
                    }
                }
            }
        }

        return new ScopeResolution(
                new EffectiveScope(global, organizationIds, provinceCodes, eventIds),
                List.copyOf(effectiveGrants)
        );
    }

    @Override
    public EffectiveScope loadCurrentRequired() {
        return loadRequired(currentUserProvider.requireUserId());
    }

    @Override
    public boolean canAccessOrganization(Long organizationId) {
        return canAccessOrganization(loadCurrentRequired(), organizationId);
    }

    @Override
    public boolean canAccessProvince(String provinceCode) {
        return canAccessProvince(loadCurrentRequired(), provinceCode);
    }

    @Override
    public boolean canAccessEvent(Long eventId) {
        return canAccessEvent(loadCurrentRequired(), eventId);
    }

    @Override
    public void requireOrganizationVisible(Long organizationId) {
        OrganizationDO organization = requireOrganization(organizationId);
        if (!canManageOrganization(loadCurrentRequired(), organization)) {
            throw resourceNotFound("组织不存在或对当前用户不可见");
        }
    }

    @Override
    public void requireOrganizationManageable(Long organizationId) {
        OrganizationDO organization = requireOrganization(organizationId);
        if (!canManageOrganization(loadCurrentRequired(), organization)) {
            throw dataForbidden("无权管理该组织");
        }
    }

    @Override
    public void requireProvinceManageable(String provinceCode) {
        Long normalizedProvince = normalizeProvinceCode(provinceCode);
        if (normalizedProvince == null
                || !dictionaryQueryService.containsEnabledValue(
                        PROVINCE_DICTIONARY,
                        String.valueOf(normalizedProvince)
                )) {
            throw resourceNotFound("省份不存在或不可用");
        }
        if (!canAccessProvince(loadCurrentRequired(), normalizedProvince)) {
            throw dataForbidden("无权管理该省份");
        }
    }

    @Override
    public void requireEventVisible(Long eventId) {
        ContestEditionScopeDO event = requireEvent(eventId);
        if (!canManageEvent(loadCurrentRequired(), event)) {
            throw resourceNotFound("赛事不存在或对当前用户不可见");
        }
    }

    @Override
    public void requireEventManageable(Long eventId) {
        ContestEditionScopeDO event = requireEvent(eventId);
        if (!canManageEvent(loadCurrentRequired(), event)) {
            throw dataForbidden("无权管理该赛事");
        }
    }

    private boolean canAccessOrganization(EffectiveScope scope, Long organizationId) {
        OrganizationDO organization = organizationId == null
                ? null
                : organizationMapper.selectById(organizationId);
        return isActiveOrganization(organization) && canManageOrganization(scope, organization);
    }

    private boolean canAccessProvince(EffectiveScope scope, String provinceCode) {
        Long normalizedProvince = normalizeProvinceCode(provinceCode);
        return normalizedProvince != null && canAccessProvince(scope, normalizedProvince);
    }

    private boolean canAccessProvince(EffectiveScope scope, Long provinceCode) {
        return scope.global() || scope.provinceCodes().contains(provinceCode);
    }

    private boolean canAccessEvent(EffectiveScope scope, Long eventId) {
        ContestEditionScopeDO event = eventId == null
                ? null
                : contestEditionMapper.selectById(eventId);
        return event != null && canManageEvent(scope, event);
    }

    private boolean canManageOrganization(EffectiveScope scope, OrganizationDO organization) {
        if (scope.global()) {
            return true;
        }
        if (scope.organizationIds().contains(organization.getId())) {
            return true;
        }
        Long provinceCode = normalizeProvinceCode(organization.getProvinceCode());
        return provinceCode != null && scope.provinceCodes().contains(provinceCode);
    }

    private boolean canManageEvent(EffectiveScope scope, ContestEditionScopeDO event) {
        if (scope.global() || scope.eventIds().contains(event.getId())) {
            return true;
        }
        if (event.getOwnerOrgId() != null && scope.organizationIds().contains(event.getOwnerOrgId())) {
            return true;
        }
        Long eventProvince = normalizeProvinceCode(event.getProvinceCode());
        if (eventProvince != null && scope.provinceCodes().contains(eventProvince)) {
            return true;
        }
        if (event.getOwnerOrgId() == null) {
            return false;
        }
        OrganizationDO owner = organizationMapper.selectById(event.getOwnerOrgId());
        Long ownerProvince = owner == null ? null : normalizeProvinceCode(owner.getProvinceCode());
        return ownerProvince != null && scope.provinceCodes().contains(ownerProvince);
    }

    private Map<Long, OrganizationDO> loadOrganizations(List<ScopeGrantDO> grants) {
        Set<Long> organizationIds = grants.stream()
                .filter(grant -> ScopeType.from(grant.getScopeType())
                        .filter(type -> type == ScopeType.ORGANIZATION)
                        .isPresent())
                .map(ScopeGrantDO::getScopeId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (organizationIds.isEmpty()) {
            return Map.of();
        }
        List<OrganizationDO> organizations = organizationMapper.selectBatchIds(organizationIds);
        Map<Long, OrganizationDO> result = new LinkedHashMap<>();
        if (organizations != null) {
            organizations.stream()
                    .filter(this::isActiveOrganization)
                    .forEach(organization -> result.put(organization.getId(), organization));
        }
        return result;
    }

    private Map<Long, ContestEditionScopeDO> loadEvents(List<ScopeGrantDO> grants) {
        Set<Long> eventIds = grants.stream()
                .filter(grant -> ScopeType.from(grant.getScopeType())
                        .filter(type -> type == ScopeType.EVENT)
                        .isPresent())
                .map(ScopeGrantDO::getScopeId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        List<ContestEditionScopeDO> events = contestEditionMapper.selectBatchIds(eventIds);
        Map<Long, ContestEditionScopeDO> result = new LinkedHashMap<>();
        if (events != null) {
            events.stream()
                    .filter(event -> event.getId() != null)
                    .forEach(event -> result.put(event.getId(), event));
        }
        return result;
    }

    private OrganizationDO requireOrganization(Long organizationId) {
        OrganizationDO organization = organizationId == null
                ? null
                : organizationMapper.selectById(organizationId);
        if (!isActiveOrganization(organization)) {
            throw resourceNotFound("组织不存在或不可用");
        }
        return organization;
    }

    private ContestEditionScopeDO requireEvent(Long eventId) {
        ContestEditionScopeDO event = eventId == null
                ? null
                : contestEditionMapper.selectById(eventId);
        if (event == null) {
            throw resourceNotFound("赛事不存在");
        }
        return event;
    }

    private boolean isAllowedOrganizationGrant(String roleCode, OrganizationDO organization) {
        if (organization == null) {
            return false;
        }
        String orgType = normalizeCode(organization.getOrgType());
        return switch (roleCode) {
            case ROLE_SCHOOL_ADMIN -> ORG_SCHOOL.equals(orgType);
            case ROLE_NATIONAL_ADMIN -> ORG_NATIONAL.equals(orgType);
            case ROLE_EVENT_MANAGER, ROLE_REVIEWER -> true;
            default -> false;
        };
    }

    private boolean isAllowedProvinceGrant(String roleCode, Long scopeId) {
        if (!ROLE_PROVINCE_ADMIN.equals(roleCode)
                && !ROLE_EVENT_MANAGER.equals(roleCode)
                && !ROLE_REVIEWER.equals(roleCode)) {
            return false;
        }
        return dictionaryQueryService.containsEnabledValue(
                PROVINCE_DICTIONARY,
                String.valueOf(scopeId)
        );
    }

    private boolean isAllowedEventGrant(String roleCode) {
        return ROLE_EVENT_MANAGER.equals(roleCode) || ROLE_REVIEWER.equals(roleCode);
    }

    private boolean isEffective(ScopeGrantDO grant, LocalDateTime now) {
        return grant != null
                && Objects.equals(GRANT_ACTIVE, grant.getStatus())
                && Objects.equals(NOT_DELETED, grant.getDeleted())
                && (grant.getValidFrom() == null || !grant.getValidFrom().isAfter(now))
                && (grant.getValidTo() == null || grant.getValidTo().isAfter(now));
    }

    private boolean isActiveOrganization(OrganizationDO organization) {
        return organization != null
                && organization.getId() != null
                && Objects.equals(ORGANIZATION_ACTIVE, organization.getStatus());
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : null;
    }

    private Long normalizeProvinceCode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private BizException resourceNotFound(String message) {
        return new BizException(ErrorCode.RESOURCE_NOT_FOUND.getCode(), message);
    }

    private BizException dataForbidden(String message) {
        return new BizException(ErrorCode.DATA_FORBIDDEN.getCode(), message);
    }

    private record ScopeResolution(
            EffectiveScope scope,
            List<EffectiveGrant> grants
    ) {
    }
}
