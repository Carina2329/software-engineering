package com.campus.contest.module.contest.controller;

import com.campus.contest.common.Result;
import com.campus.contest.module.contest.dto.EditionPublishRequest;
import com.campus.contest.module.contest.dto.EditionUpdateRequest;
import com.campus.contest.module.contest.dto.MilestoneCreateRequest;
import com.campus.contest.module.contest.service.EditionService;
import com.campus.contest.module.contest.service.MilestoneService;
import com.campus.contest.module.contest.vo.EditionDetailVO;
import com.campus.contest.module.contest.vo.EditionPublishVO;
import com.campus.contest.module.contest.vo.EditionVersionVO;
import com.campus.contest.module.contest.vo.MilestoneCreateVO;
import com.campus.contest.module.contest.vo.MilestoneVO;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/editions")
@RequiredArgsConstructor
public class EditionController {

    private final EditionService editionService;
    private final MilestoneService milestoneService;

    /** EP-017 */
    @GetMapping("/{edition_id}")
    @PreAuthorize("isAuthenticated()")
    public Result<EditionDetailVO> get(@PathVariable("edition_id") Long editionId) {
        return Result.ok(editionService.get(editionId));
    }

    /** EP-018 */
    @PatchMapping("/{edition_id}")
    @PreAuthorize("hasAuthority('edition:update')")
    public Result<EditionVersionVO> update(
            @PathVariable("edition_id") Long editionId,
            @Valid @RequestBody EditionUpdateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return Result.ok(editionService.update(editionId, request, idempotencyKey));
    }

    /** EP-019 */
    @PostMapping("/{edition_id}/publish")
    @PreAuthorize("hasAuthority('edition:publish')")
    public Result<EditionPublishVO> publish(
            @PathVariable("edition_id") Long editionId,
            @Valid @RequestBody EditionPublishRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return Result.ok(editionService.publish(editionId, request, idempotencyKey));
    }

    /** EP-020 */
    @GetMapping("/{edition_id}/milestones")
    @PreAuthorize("isAuthenticated()")
    public Result<List<MilestoneVO>> listMilestones(
            @PathVariable("edition_id") Long editionId
    ) {
        return Result.ok(milestoneService.list(editionId));
    }

    /** EP-021 */
    @PostMapping("/{edition_id}/milestones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('edition:milestone:create')")
    public Result<MilestoneCreateVO> createMilestone(
            @PathVariable("edition_id") Long editionId,
            @Valid @RequestBody MilestoneCreateRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return Result.ok(milestoneService.create(editionId, request, idempotencyKey));
    }
}
