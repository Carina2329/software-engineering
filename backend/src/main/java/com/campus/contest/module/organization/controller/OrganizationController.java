package com.campus.contest.module.organization.controller;

import com.campus.contest.common.Result;
import com.campus.contest.module.organization.dto.OrganizationCreateRequest;
import com.campus.contest.module.organization.dto.OrganizationPageQuery;
import com.campus.contest.module.organization.dto.OrganizationUpdateRequest;
import com.campus.contest.module.organization.service.OrganizationService;
import com.campus.contest.module.organization.vo.OrganizationCreatedVO;
import com.campus.contest.module.organization.vo.OrganizationItemVO;
import com.campus.contest.module.organization.vo.OrganizationPageVO;
import com.campus.contest.module.system.vo.IdVersionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * EP-086 至 EP-089：组织主档。
 */
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    /** EP-086 分页查询组织 */
    @GetMapping
    @PreAuthorize("hasAuthority('organization:list')")
    public Result<OrganizationPageVO> listOrganizations(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "org_type", required = false) String orgType,
            @RequestParam(value = "province_code", required = false) String provinceCode,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        OrganizationPageQuery query = new OrganizationPageQuery();
        query.setPage(page);
        query.setSize(size);
        query.setOrgType(orgType);
        query.setProvinceCode(provinceCode);
        query.setKeyword(keyword);
        return Result.ok(organizationService.pageOrganizations(query));
    }

    /** EP-087 创建组织 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('organization:create')")
    public Result<OrganizationCreatedVO> createOrganization(
            @RequestBody OrganizationCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return Result.ok(organizationService.createOrganization(request, idempotencyKey));
    }

    /** EP-088 查询组织详情 */
    @GetMapping("/{organization_id}")
    @PreAuthorize("hasAuthority('organization:list')")
    public Result<OrganizationItemVO> getOrganization(
            @PathVariable("organization_id") Long organizationId
    ) {
        return Result.ok(organizationService.getOrganization(organizationId));
    }

    /** EP-089 修改组织 */
    @PatchMapping("/{organization_id}")
    @PreAuthorize("hasAuthority('organization:update')")
    public Result<IdVersionVO> updateOrganization(
            @PathVariable("organization_id") Long organizationId,
            @RequestBody OrganizationUpdateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return Result.ok(organizationService.updateOrganization(
                organizationId,
                request,
                idempotencyKey
        ));
    }
}
