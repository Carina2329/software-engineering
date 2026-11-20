package com.campus.contest.module.organization.controller;

import com.campus.contest.common.Result;
import com.campus.contest.module.organization.dto.SchoolAffiliationPageQuery;
import com.campus.contest.module.organization.dto.SchoolAffiliationReviewRequest;
import com.campus.contest.module.organization.service.SchoolAffiliationService;
import com.campus.contest.module.organization.vo.SchoolAffiliationPageVO;
import com.campus.contest.module.organization.vo.SchoolAffiliationReviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * EP-092 至 EP-093：学校归属审核。
 */
@RestController
@RequestMapping("/api/v1/school-affiliations")
@RequiredArgsConstructor
public class SchoolAffiliationController {

    private final SchoolAffiliationService schoolAffiliationService;

    /** EP-092 查询学校归属申请 */
    @GetMapping
    @PreAuthorize("hasAuthority('school:affiliation:list')")
    public Result<SchoolAffiliationPageVO> listAffiliations(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "school_id", required = false) String schoolId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        SchoolAffiliationPageQuery query = new SchoolAffiliationPageQuery();
        query.setStatus(status);
        query.setSchoolId(schoolId);
        query.setKeyword(keyword);
        query.setPage(page);
        query.setSize(size);
        return Result.ok(schoolAffiliationService.pageAffiliations(query));
    }

    /** EP-093 审核学校归属 */
    @PatchMapping("/{user_id}")
    @PreAuthorize("hasAuthority('school:affiliation:review')")
    public Result<SchoolAffiliationReviewVO> reviewAffiliation(
            @PathVariable("user_id") Long userId,
            @RequestBody SchoolAffiliationReviewRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return Result.ok(schoolAffiliationService.reviewAffiliation(
                userId,
                request,
                idempotencyKey
        ));
    }
}
