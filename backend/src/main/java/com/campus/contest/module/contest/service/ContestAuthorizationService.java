package com.campus.contest.module.contest.service;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.contest.mapper.ContestAccessCriteria;
import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.organization.model.EffectiveScope;
import com.campus.contest.module.organization.service.OrganizationScopeService;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Set;

/**
 * 竞赛模块的数据范围适配器。
 *
 * 组织、省份和赛事授权统一来自 OrganizationScopeService；本类只补充管理按钮权限
 * 与普通用户已确认学校信息，不复制范围授权矩阵。
 */
@Service
@RequiredArgsConstructor
public class ContestAuthorizationService {

    private static final Set<String> MANAGER_PERMISSIONS = Set.of(
            "contest:create",
            "contest:update",
            "edition:create",
            "edition:update",
            "edition:publish",
            "edition:milestone:create",
            "edition:milestone:update"
    );

    private final OrganizationScopeService organizationScopeService;
    private final CurrentUserProvider currentUserProvider;
    private final SysUserMapper userMapper;
    private final OrganizationMapper organizationMapper;

    public boolean canManage() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(MANAGER_PERMISSIONS::contains);
    }

    public ContestAccessCriteria currentAccessCriteria() {
        Long userId = currentUserProvider.requireUserId();
        EffectiveScope scope = organizationScopeService.loadRequired(userId);
        ContestAccessCriteria criteria = new ContestAccessCriteria();
        criteria.setManager(canManage());
        criteria.setGlobal(scope.global());
        criteria.setOrganizationIds(new ArrayList<>(scope.organizationIds()));
        criteria.setProvinceCodes(new ArrayList<>(scope.provinceCodes()));
        criteria.setEventIds(new ArrayList<>(scope.eventIds()));

        SysUserDO user = userMapper.selectById(userId);
        if (user != null
                && "VERIFIED".equalsIgnoreCase(user.getSchoolVerificationStatus())
                && user.getSchoolId() != null) {
            OrganizationDO school = organizationMapper.selectById(user.getSchoolId());
            if (school != null && Integer.valueOf(1).equals(school.getStatus())) {
                criteria.setViewerSchoolId(school.getId());
                criteria.setViewerProvinceCode(normalizeProvince(school.getProvinceCode()));
            }
        }
        return criteria;
    }

    public EffectiveScope currentScope() {
        return organizationScopeService.loadCurrentRequired();
    }

    public void requirePlatformAdmin() {
        if (!currentScope().global()) {
            throw dataForbidden("只有平台管理员可以维护竞赛系列");
        }
    }

    public void requireOrganizationManageable(Long organizationId) {
        organizationScopeService.requireOrganizationManageable(organizationId);
    }

    public void requireProvinceManageable(String provinceCode) {
        organizationScopeService.requireProvinceManageable(provinceCode);
    }

    public void requireEventManageable(Long editionId) {
        organizationScopeService.requireEventManageable(editionId);
    }

    public OrganizationDO requireActiveOrganization(Long organizationId) {
        OrganizationDO organization = organizationId == null
                ? null
                : organizationMapper.selectById(organizationId);
        if (organization == null || !Integer.valueOf(1).equals(organization.getStatus())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND.getCode(), "组织不存在或不可用");
        }
        return organization;
    }

    public Long normalizeProvince(String provinceCode) {
        if (provinceCode == null || provinceCode.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(provinceCode.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BizException dataForbidden(String message) {
        return new BizException(ErrorCode.DATA_FORBIDDEN.getCode(), message);
    }
}
