package com.campus.contest.security;

/**
 * 从 Spring Security 上下文中读取当前认证用户。
 */
public interface CurrentUserProvider {

    Long requireUserId();

    String requireSessionId();
}
