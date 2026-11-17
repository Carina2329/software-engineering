package com.campus.contest.module.system.service;

import com.campus.contest.module.system.dto.ProfileUpdateRequest;
import com.campus.contest.module.system.vo.CurrentUserVO;
import com.campus.contest.module.system.vo.IdVersionVO;

/**
 * 个人档案（BIZ-PROFILE-001 / EP-003 档案字段适配 / EP-004）。
 *
 * 本任务只开放“本人查看和修改本人档案”：
 * - 学生可维护姓名、学号、学院、专业、年级；
 * - 教师可维护姓名、学院、职称；
 * - 账号状态、角色、权限、密码和登录方式不属于个人档案，接口不提供任何修改入口。
 */
public interface ProfileService {

    /**
     * 为 EP-003 响应补齐角色对应的档案字段与最新 version。
     *
     * 由 UserController 在调用 {@link UserAccessService#currentUserProfile()} 后执行，
     * 这样 EP-005/EP-007 的既有用户管理逻辑保持不变。
     */
    void applyProfileFields(CurrentUserVO vo);

    /**
     * EP-004 修改当前登录用户的个人档案。
     *
     * @param request        档案修改请求，必须携带 version
     * @param idempotencyKey 可选的 Idempotency-Key；提供时同一用户、同一内容的重放只生效一次
     * @return 用户 ID 与更新后的 version
     */
    IdVersionVO updateMyProfile(ProfileUpdateRequest request, String idempotencyKey);
}
