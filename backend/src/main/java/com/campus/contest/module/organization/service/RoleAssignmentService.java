package com.campus.contest.module.organization.service;

import com.campus.contest.module.organization.dto.RoleAssignmentCreateRequest;
import com.campus.contest.module.organization.dto.RoleAssignmentUpdateRequest;
import com.campus.contest.module.system.vo.IdVersionVO;

/**
 * EP-090 至 EP-091：角色范围授权的唯一写入口。
 */
public interface RoleAssignmentService {

    IdVersionVO createAssignment(
            RoleAssignmentCreateRequest request,
            String idempotencyKey
    );

    IdVersionVO updateAssignment(
            Long assignmentId,
            RoleAssignmentUpdateRequest request,
            String idempotencyKey
    );
}
