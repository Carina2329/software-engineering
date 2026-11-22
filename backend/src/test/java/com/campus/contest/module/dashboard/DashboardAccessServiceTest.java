package com.campus.contest.module.dashboard;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.organization.model.EffectiveScope;
import com.campus.contest.module.organization.service.OrganizationScopeService;
import com.campus.contest.module.system.dto.AuthorizationSnapshot;
import com.campus.contest.module.system.service.AuthorizationSnapshotService;
import com.campus.contest.module.dashboard.service.DashboardAccessService;
import com.campus.contest.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardAccessServiceTest {

    private AuthorizationSnapshotService authorizationService;
    private OrganizationScopeService scopeService;
    private DashboardAccessService accessService;

    @BeforeEach
    void setUp() {
        authorizationService = mock(AuthorizationSnapshotService.class);
        scopeService = mock(OrganizationScopeService.class);
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        when(currentUserProvider.requireUserId()).thenReturn(1L);
        accessService = new DashboardAccessService(
                authorizationService,
                scopeService,
                currentUserProvider
        );
    }

    @Test
    void globalAdministratorWithPermissionPasses() {
        when(authorizationService.loadRequired(1L)).thenReturn(snapshot(Set.of("dashboard:view")));
        when(scopeService.loadRequired(1L)).thenReturn(new EffectiveScope(
                true, Set.of(), Set.of(), Set.of()
        ));

        assertThatCode(accessService::requireCurrentAccess).doesNotThrowAnyException();
    }

    @Test
    void missingFunctionPermissionReturns403() {
        when(authorizationService.loadRequired(1L)).thenReturn(snapshot(Set.of("contest:read")));

        assertThatThrownBy(accessService::requireCurrentAccess)
                .isInstanceOfSatisfying(BizException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.FUNCTION_FORBIDDEN.getCode()));
    }

    @Test
    void nonGlobalAdministratorReturns403() {
        when(authorizationService.loadRequired(1L)).thenReturn(snapshot(Set.of("dashboard:view")));
        when(scopeService.loadRequired(1L)).thenReturn(EffectiveScope.empty());

        assertThatThrownBy(accessService::requireCurrentAccess)
                .isInstanceOfSatisfying(BizException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.DATA_FORBIDDEN.getCode()));
    }

    private AuthorizationSnapshot snapshot(Set<String> permissions) {
        return new AuthorizationSnapshot(
                1L,
                "admin",
                1,
                Set.of("ADMIN"),
                permissions,
                Set.of("ALL")
        );
    }
}
