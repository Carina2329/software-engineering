package com.campus.contest.module.organization.controller;

import com.campus.contest.common.Result;
import com.campus.contest.module.organization.dto.SchoolPageQuery;
import com.campus.contest.module.organization.service.SchoolService;
import com.campus.contest.module.organization.vo.SchoolPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * EP-085：查询启用学校。
 */
@RestController
@RequestMapping("/api/v1/schools")
@RequiredArgsConstructor
public class SchoolController {

    private final SchoolService schoolService;

    @GetMapping
    @PreAuthorize("hasAuthority('school:list')")
    public Result<SchoolPageVO> listSchools(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "province_code", required = false) String provinceCode
    ) {
        SchoolPageQuery query = new SchoolPageQuery();
        query.setPage(page);
        query.setSize(size);
        query.setKeyword(keyword);
        query.setProvinceCode(provinceCode);
        return Result.ok(schoolService.pageSchools(query));
    }
}
