package com.campus.contest.module.team.controller;

import com.campus.contest.common.Result;
import com.campus.contest.module.team.dto.EntryGrantBatchRequest;
import com.campus.contest.module.team.service.EditionEntryGrantService;
import com.campus.contest.module.team.vo.EntryGrantBatchVO;
import com.campus.contest.module.team.vo.EntryGrantListVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EditionEntryGrantController {

    private final EditionEntryGrantService entryGrantService;

    /** EP-096 */
    @PostMapping("/editions/{edition_id}/entry-grants/batch")
    @PreAuthorize("hasAuthority('team:eligibility:manage')")
    public Result<EntryGrantBatchVO> batch(
            @PathVariable("edition_id") Long editionId,
            @Valid @RequestBody EntryGrantBatchRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return Result.ok(entryGrantService.batch(editionId, request, idempotencyKey));
    }

    /** EP-097 */
    @GetMapping("/editions/{edition_id}/entry-grants")
    @PreAuthorize("hasAuthority('team:eligibility:manage')")
    public Result<EntryGrantListVO> list(
            @PathVariable("edition_id") Long editionId,
            @RequestParam(value = "status", required = false) String status
    ) {
        return Result.ok(entryGrantService.list(editionId, status));
    }
}
