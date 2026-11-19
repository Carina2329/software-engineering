package com.campus.contest.module.organization.service;

import com.campus.contest.module.organization.model.EffectiveScope;
import com.campus.contest.module.organization.model.EffectiveGrant;

import java.util.List;

/**
 * 组织、省份和赛事的数据范围判断统一入口。
 *
 * 功能权限仍由 Spring Security 校验；本服务只判断当前用户能否访问指定数据范围。
 */
public interface OrganizationScopeService {

    /**
     * 读取指定用户经有效期、状态和目标存在性校验后的范围授权。
     */
    EffectiveScope loadRequired(Long userId);

    /**
     * 读取当前登录用户的有效范围授权。
     */
    EffectiveScope loadCurrentRequired();

    /**
     * 返回当前用户的有效管理角色和具体范围，供授权矩阵判断使用。
     */
    List<EffectiveGrant> loadEffectiveGrants(Long userId);

    boolean canAccessOrganization(Long organizationId);

    boolean canAccessProvince(String provinceCode);

    boolean canAccessEvent(Long eventId);

    /**
     * 查询失败使用 404，避免泄露调用人授权范围外的数据是否存在。
     */
    void requireOrganizationVisible(Long organizationId);

    /**
     * 写操作允许返回 403；资源不存在仍返回 404。
     */
    void requireOrganizationManageable(Long organizationId);

    void requireProvinceManageable(String provinceCode);

    void requireEventVisible(Long eventId);

    void requireEventManageable(Long eventId);
}
