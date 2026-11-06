package com.campus.contest.module.system.service;

import com.campus.contest.module.system.dto.PasswordResetConfirm;
import com.campus.contest.module.system.dto.RegisterRequest;
import com.campus.contest.module.system.vo.RegisterResultVO;

/**
 * 注册与找回密码账号流程。
 */
public interface AccountService {

    RegisterResultVO register(RegisterRequest request, String clientIp);

    Long confirmPasswordReset(PasswordResetConfirm request, String clientIp);
}
