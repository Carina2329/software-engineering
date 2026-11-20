package com.campus.contest.module.organization.service;

import com.campus.contest.module.organization.dto.OrganizationCreateRequest;
import com.campus.contest.module.organization.dto.OrganizationPageQuery;
import com.campus.contest.module.organization.dto.OrganizationUpdateRequest;
import com.campus.contest.module.organization.vo.OrganizationCreatedVO;
import com.campus.contest.module.organization.vo.OrganizationItemVO;
import com.campus.contest.module.organization.vo.OrganizationPageVO;
import com.campus.contest.module.system.vo.IdVersionVO;

/**
 * EP-086 至 EP-089：组织主档查询和维护。
 */
public interface OrganizationService {

    OrganizationPageVO pageOrganizations(OrganizationPageQuery query);

    OrganizationCreatedVO createOrganization(
            OrganizationCreateRequest request,
            String idempotencyKey
    );

    OrganizationItemVO getOrganization(Long organizationId);

    IdVersionVO updateOrganization(
            Long organizationId,
            OrganizationUpdateRequest request,
            String idempotencyKey
    );
}
