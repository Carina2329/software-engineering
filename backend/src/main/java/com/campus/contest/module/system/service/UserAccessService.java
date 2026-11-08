package com.campus.contest.module.system.service;

import com.campus.contest.module.system.dto.UserAccessUpdateRequest;
import com.campus.contest.module.system.dto.UserPageQuery;
import com.campus.contest.module.system.vo.IdVersionVO;
import com.campus.contest.module.system.vo.UserPageVO;

/**
 * 用户可见范围与角色分配（EP-005 / EP-007）。
 */
public interface UserAccessService {

    /** EP-005 用户列表，支持分页、排序、用户名、用户类型和状态筛选 */
    UserPageVO pageUsers(UserPageQuery query);

    /** EP-007 修改用户状态和角色，必须携带 user.version */
    IdVersionVO updateUserAccess(Long userId, UserAccessUpdateRequest request, String idempotencyKey);
}
