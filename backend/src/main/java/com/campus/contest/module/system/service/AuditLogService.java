package com.campus.contest.module.system.service;

import com.campus.contest.module.system.dto.AuditLogCommand;

/**
 * 登录、授权和关键写操作的统一审计入口。
 */
public interface AuditLogService {

    void record(AuditLogCommand command);
}
