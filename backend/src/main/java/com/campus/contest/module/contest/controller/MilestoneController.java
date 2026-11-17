package com.campus.contest.module.contest.controller;

import com.campus.contest.common.Result;
import com.campus.contest.module.contest.dto.MilestoneUpdateRequest;
import com.campus.contest.module.contest.service.MilestoneService;
import com.campus.contest.module.contest.vo.MilestoneVersionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/milestones")
@RequiredArgsConstructor
public class MilestoneController {

    private final MilestoneService milestoneService;

    /** EP-022 */
    @PatchMapping("/{milestone_id}")
    @PreAuthorize("hasAuthority('edition:milestone:update')")
    public Result<MilestoneVersionVO> update(
            @PathVariable("milestone_id") Long milestoneId,
            @Valid @RequestBody MilestoneUpdateRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return Result.ok(milestoneService.update(milestoneId, request, idempotencyKey));
    }
}
