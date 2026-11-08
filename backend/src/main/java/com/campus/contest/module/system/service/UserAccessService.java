package com.campus.contest.module.system.service;

import com.campus.contest.module.system.dto.UserAccessUpdateRequest;
import com.campus.contest.module.system.dto.UserPageQuery;
import com.campus.contest.module.system.vo.CurrentUserVO;
import com.campus.contest.module.system.vo.IdVersionVO;
import com.campus.contest.module.system.vo.UserPageVO;

/**
 * 用户可见范围与角色分配（EP-003 / EP-005 / EP-007）。
 */
public interface UserAccessService {

    /** EP-003 查询当前用户：档案、角色、数据范围 */
    CurrentUserVO currentUserProfile();

    /** EP-005 用户列表，支持分页、排序、用户名、用户类型和状态筛选 */
    UserPageVO pageUsers(UserPageQuery query);

    /** EP-007 修改用户状态和角色，必须携带 user.version 与 Idempotency-Key */
    IdVersionVO updateUserAccess(Long userId, UserAccessUpdateRequest request, String idempotencyKey);
}
