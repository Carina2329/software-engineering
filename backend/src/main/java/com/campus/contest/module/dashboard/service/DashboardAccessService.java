package com.campus.contest.module.dashboard.service;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.organization.service.OrganizationScopeService;
import com.campus.contest.module.system.dto.AuthorizationSnapshot;
import com.campus.contest.module.system.service.AuthorizationSnapshotService;
import com.campus.contest.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardAccessService {

    public static final String VIEW_PERMISSION = "dashboard:view";

    private final AuthorizationSnapshotService authorizationSnapshotService;
    private final OrganizationScopeService organizationScopeService;
    private final CurrentUserProvider currentUserProvider;

    public void requireCurrentAccess() {
        requireAccess(currentUserProvider.requireUserId());
    }

    public void requireAccess(Long userId) {
        AuthorizationSnapshot snapshot = authorizationSnapshotService.loadRequired(userId);
        if (snapshot.permissionCodes() == null
                || !snapshot.permissionCodes().contains(VIEW_PERMISSION)) {
            throw new BizException(
                    ErrorCode.FUNCTION_FORBIDDEN.getCode(),
                    "缺少数字大屏查看权限"
            );
        }
        if (!organizationScopeService.loadRequired(userId).global()) {
            throw new BizException(
                    ErrorCode.DATA_FORBIDDEN.getCode(),
                    "数字大屏仅对 GLOBAL 平台管理员开放"
            );
        }
    }
}
