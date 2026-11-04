package com.campus.contest.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Spring Security 认证主体，仅保存当前请求所需的非敏感会话信息。
 */
public record AuthenticatedUser(
        Long userId,
        String sessionId,
        Set<String> roleCodes,
        Set<String> permissionCodes,
        Set<String> dataScopes
) {

    public AuthenticatedUser {
        roleCodes = roleCodes == null ? Set.of() : Set.copyOf(roleCodes);
        permissionCodes = permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes);
        dataScopes = dataScopes == null ? Set.of() : Set.copyOf(dataScopes);
    }

    public Collection<? extends GrantedAuthority> authorities() {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        roleCodes.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        permissionCodes.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        return Set.copyOf(authorities);
    }
}
