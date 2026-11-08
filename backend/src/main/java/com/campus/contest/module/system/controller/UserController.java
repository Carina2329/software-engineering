package com.campus.contest.module.system.controller;

import com.campus.contest.common.Result;
import com.campus.contest.module.system.dto.UserAccessUpdateRequest;
import com.campus.contest.module.system.dto.UserPageQuery;
import com.campus.contest.module.system.service.UserAccessService;
import com.campus.contest.module.system.vo.CurrentUserVO;
import com.campus.contest.module.system.vo.IdVersionVO;
import com.campus.contest.module.system.vo.UserPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * EP-003 查询当前用户、EP-005 查看用户、EP-007 修改用户状态和角色。
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserAccessService userAccessService;

    /** EP-003 查询当前用户：档案、角色、数据范围与权限摘要 */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public Result<CurrentUserVO> currentUser() {
        return Result.ok(userAccessService.currentUserProfile());
    }

    /** EP-005 用户列表 */
    @GetMapping
    @PreAuthorize("hasAuthority('system:user:list')")
    public Result<UserPageVO> listUsers(UserPageQuery query) {
        return Result.ok(userAccessService.pageUsers(query));
    }

    /**
     * EP-007 修改用户状态和角色。
     *
     * OpenAPI 把 Idempotency-Key 标为必填，所以这里仍然只声明为可选，
     * 由 Service 统一校验后返回 400 + 10001，保持错误码风格一致（而不是框架层 400）。
     */
    @PatchMapping("/{user_id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    public Result<IdVersionVO> updateUserAccess(
            @PathVariable("user_id") Long userId,
            @RequestBody UserAccessUpdateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return Result.ok(userAccessService.updateUserAccess(userId, request, idempotencyKey));
    }
}
