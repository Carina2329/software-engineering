package com.campus.contest.module.contest.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class ContestAuthorizationService {

    private static final Set<String> MANAGER_PERMISSIONS = Set.of(
            "contest:create",
            "contest:update",
            "edition:create",
            "edition:update",
            "edition:publish",
            "edition:milestone:create",
            "edition:milestone:update"
    );

    public boolean canManage() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(MANAGER_PERMISSIONS::contains);
    }
}
