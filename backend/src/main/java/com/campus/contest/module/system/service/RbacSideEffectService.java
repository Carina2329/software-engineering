package com.campus.contest.module.system.service;

import java.util.Collection;

/**
 * RBAC 写操作的公共副作用入口：审计日志、请求上下文和统一会话撤销。
 *
 * 本接口只允许调用同学 1 提供的 {@link com.campus.contest.security.SessionRevocationService}，
 * 不实现第二套 Redis 会话撤销逻辑。
 */
public interface RbacSideEffectService {

    /**
     * 记录一条 RBAC 审计日志。params 必须是脱敏后的 JSON，不能包含密码、OTP、JWT 或 Refresh Token。
     */
    void audit(Long operatorId, String operation, String params, Integer result);

    /**
     * 数据库事务提交后撤销指定用户的全部 Redis 会话。
     * 撤销失败时按 contract.md 要求做有限次重试，仍失败则记录 SESSION_REVOCATION_FAILED
     * 并抛 HTTP 503 + 50003。
     */
    void revokeSessionsOfUsers(Collection<Long> userIds, String operation);

    /**
     * 当前请求的客户端 IP，非 Web 上下文时返回 null。
     */
    String clientIp();
}
