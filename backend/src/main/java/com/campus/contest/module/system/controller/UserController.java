package com.campus.contest.module.system.controller;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.Result;
import com.campus.contest.module.system.dto.ProfileUpdateRequest;
import com.campus.contest.module.system.dto.UserAccessUpdateRequest;
import com.campus.contest.module.system.dto.UserPageQuery;
import com.campus.contest.module.system.service.ProfileService;
import com.campus.contest.module.system.service.UserAccessService;
import com.campus.contest.module.system.vo.CurrentUserVO;
import com.campus.contest.module.system.vo.IdVersionVO;
import com.campus.contest.module.system.vo.UserPageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * EP-003 查询当前用户、EP-004 修改当前用户档案、EP-005 查看用户、EP-007 修改用户状态和角色。
 *
 * 说明：EP-005 / EP-007 的既有逻辑完全复用 UserAccessService，本任务没有改动它们的实现。
 * ProfileService 通过 ObjectProvider 装配，原因见下方字段注释。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserAccessService userAccessService;

    /**
     * 个人档案服务。
     *
     * 用 ObjectProvider 而不是直接构造注入，是为了兼容 RbacHttpAccessTest 这类手工
     * {@code @Import} 装配的测试上下文（该测试文件属于其他任务的既有测试，不在本任务可修改范围）。
     * 生产环境由 Spring 组件扫描注入真实实现；一旦缺席：
     * - GET /users/me 仍返回 EP-003 基础档案，并打印告警；
     * - PATCH /users/me 直接返回明确的系统错误，不会静默成功。
     * 段欣懿合入时若已在 RbacHttpAccessTest 的 @Import 中加入 ProfileServiceImpl.class，
     * 可以把这里改回普通构造注入。
     */
    private final ProfileService profileService;

    public UserController(
            UserAccessService userAccessService,
            ObjectProvider<ProfileService> profileServiceProvider
    ) {
        this.userAccessService = userAccessService;
        this.profileService = profileServiceProvider.getIfAvailable();
    }

    /**
     * EP-003 查询当前用户：档案、角色、数据范围与权限摘要。
     *
     * 档案字段仍由 UserAccessService 组装，个人档案任务只在其上补齐
     * student_no、title 与 version，并保证只暴露当前角色可维护的字段。
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public Result<CurrentUserVO> currentUser() {
        CurrentUserVO vo = userAccessService.currentUserProfile();
        if (profileService == null) {
            log.warn("个人档案服务未装配，GET /users/me 只返回 EP-003 基础档案字段");
            return Result.ok(vo);
        }
        profileService.applyProfileFields(vo);
        return Result.ok(vo);
    }

    /**
     * EP-004 修改当前用户档案。
     *
     * 路径里没有 user_id，服务端只以 Spring Security 上下文中的当前用户为准，
     * 因此本接口不存在“修改他人档案”的入口。
     */
    @PatchMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public Result<IdVersionVO> updateMyProfile(
            @RequestBody ProfileUpdateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        if (profileService == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "个人档案服务未装配");
        }
        return Result.ok(profileService.updateMyProfile(request, idempotencyKey));
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
