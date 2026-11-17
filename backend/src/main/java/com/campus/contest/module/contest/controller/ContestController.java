package com.campus.contest.module.contest.controller;

import com.campus.contest.common.Result;
import com.campus.contest.module.contest.dto.ContestCreateRequest;
import com.campus.contest.module.contest.dto.ContestPageQuery;
import com.campus.contest.module.contest.dto.ContestUpdateRequest;
import com.campus.contest.module.contest.dto.EditionCreateRequest;
import com.campus.contest.module.contest.dto.EditionPageQuery;
import com.campus.contest.module.contest.service.ContestService;
import com.campus.contest.module.contest.service.EditionService;
import com.campus.contest.module.contest.vo.ContestCreateVO;
import com.campus.contest.module.contest.vo.ContestDetailVO;
import com.campus.contest.module.contest.vo.ContestPageVO;
import com.campus.contest.module.contest.vo.ContestVersionVO;
import com.campus.contest.module.contest.vo.EditionCreateVO;
import com.campus.contest.module.contest.vo.EditionPageVO;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contests")
@RequiredArgsConstructor
public class ContestController {

    private final ContestService contestService;
    private final EditionService editionService;

    /** EP-011 */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<ContestPageVO> list(ContestPageQuery query) {
        return Result.ok(contestService.page(query));
    }

    /** EP-012 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('contest:create')")
    public Result<ContestCreateVO> create(
            @Valid @RequestBody ContestCreateRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return Result.ok(contestService.create(request, idempotencyKey));
    }

    /** EP-013 */
    @GetMapping("/{contest_id}")
    @PreAuthorize("isAuthenticated()")
    public Result<ContestDetailVO> get(@PathVariable("contest_id") Long contestId) {
        return Result.ok(contestService.get(contestId));
    }

    /** EP-014 */
    @PatchMapping("/{contest_id}")
    @PreAuthorize("hasAuthority('contest:update')")
    public Result<ContestVersionVO> update(
            @PathVariable("contest_id") Long contestId,
            @Valid @RequestBody ContestUpdateRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return Result.ok(contestService.update(contestId, request, idempotencyKey));
    }

    /** EP-015 */
    @GetMapping("/{contest_id}/editions")
    @PreAuthorize("isAuthenticated()")
    public Result<EditionPageVO> listEditions(
            @PathVariable("contest_id") Long contestId,
            EditionPageQuery query
    ) {
        return Result.ok(editionService.page(contestId, query));
    }

    /** EP-016 */
    @PostMapping("/{contest_id}/editions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('edition:create')")
    public Result<EditionCreateVO> createEdition(
            @PathVariable("contest_id") Long contestId,
            @Valid @RequestBody EditionCreateRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return Result.ok(editionService.create(contestId, request, idempotencyKey));
    }
}
