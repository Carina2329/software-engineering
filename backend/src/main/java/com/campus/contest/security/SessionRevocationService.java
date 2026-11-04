package com.campus.contest.security;

import java.util.Set;

/**
 * 会话查询和吊销的统一入口。
 */
public interface SessionRevocationService {

    /**
     * 吊销指定会话。
     */
    void revokeSession(String sessionId);

    /**
     * 吊销指定用户的全部会话。
     */
    void revokeAllSessions(Long userId);

    /**
     * 返回指定用户当前仍有效的会话标识，供审计和内部维护使用。
     */
    Set<String> findSessionIds(Long userId);
}
