package com.campus.contest.module.organization.controller;

import com.campus.contest.common.Result;
import com.campus.contest.module.organization.dto.RoleAssignmentCreateRequest;
import com.campus.contest.module.organization.dto.RoleAssignmentUpdateRequest;
import com.campus.contest.module.organization.service.RoleAssignmentService;
import com.campus.contest.module.system.vo.IdVersionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * EP-090 至 EP-091：范围角色授权。
 */
@RestController
@RequestMapping("/api/v1/role-assignments")
@RequiredArgsConstructor
public class RoleAssignmentController {

    private final RoleAssignmentService roleAssignmentService;

    /** EP-090 授予范围角色 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('organization:role:assign')")
    public Result<IdVersionVO> createAssignment(
            @RequestBody RoleAssignmentCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return Result.ok(roleAssignmentService.createAssignment(request, idempotencyKey));
    }

    /** EP-091 修改或撤销范围角色 */
    @PatchMapping("/{assignment_id}")
    @PreAuthorize("hasAuthority('organization:role:assign')")
    public Result<IdVersionVO> updateAssignment(
            @PathVariable("assignment_id") Long assignmentId,
            @RequestBody RoleAssignmentUpdateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return Result.ok(roleAssignmentService.updateAssignment(
                assignmentId,
                request,
                idempotencyKey
        ));
    }
}
