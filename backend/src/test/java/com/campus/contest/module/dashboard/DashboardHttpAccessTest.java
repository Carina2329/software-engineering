package com.campus.contest.module.dashboard;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.GlobalExceptionHandler;
import com.campus.contest.common.trace.TraceIdFilter;
import com.campus.contest.config.CorsConfig;
import com.campus.contest.module.dashboard.controller.DashboardController;
import com.campus.contest.module.dashboard.controller.support.DashboardAccessDeniedAdvice;
import com.campus.contest.module.dashboard.service.DashboardOverviewService;
import com.campus.contest.module.dashboard.service.DashboardTicketService;
import com.campus.contest.module.dashboard.vo.DashboardContestOperationsVO;
import com.campus.contest.module.dashboard.vo.DashboardLevelDistributionVO;
import com.campus.contest.module.dashboard.vo.DashboardOverviewVO;
import com.campus.contest.module.dashboard.vo.DashboardSummaryVO;
import com.campus.contest.module.dashboard.vo.DashboardTicketVO;
import com.campus.contest.security.AuthSessionService;
import com.campus.contest.security.AuthenticatedUser;
import com.campus.contest.security.JwtService;
import com.campus.contest.security.SecurityConfig;
import com.campus.contest.security.SecurityCurrentUserProvider;
import com.campus.contest.security.SecurityErrorWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(classes = DashboardHttpAccessTest.TestApplication.class)
@AutoConfigureMockMvc
class DashboardHttpAccessTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DashboardOverviewService overviewService;
    @Autowired
    private DashboardTicketService ticketService;

    @BeforeEach
    void clearMockInvocations() {
        clearInvocations(overviewService, ticketService);
    }

    @Test
    void administratorCanReadCompleteSnapshot() throws Exception {
        when(overviewService.getCurrentOverview()).thenReturn(overview());

        MvcResult result = mockMvc.perform(get("/api/v1/dashboard/overview")
                        .with(authentication(dashboardAuthentication(Set.of("dashboard:view")))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = read(result);
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("trace_id").asText()).isNotBlank();
        JsonNode data = body.path("data");
        assertThat(data.path("generated_at").asText()).isEqualTo("2026-11-21T17:30:00+08:00");
        assertThat(data.path("data_source").asText()).isEqualTo("平台组织、用户及竞赛届次业务库");
        assertThat(data.path("summary").path("effective_school_count").asLong()).isEqualTo(4);
        assertThat(data.path("contest_operations").path("level_distribution").get(0)
                .path("level").asText()).isEqualTo("SCHOOL");
        assertThat(data.path("province_schools").isArray()).isTrue();
        assertThat(data.path("user_roles").isArray()).isTrue();
        assertThat(data.path("upcoming_deadlines").isArray()).isTrue();
    }

    @Test
    void userWithoutDashboardPermissionGets403() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/dashboard/overview")
                        .with(authentication(dashboardAuthentication(Set.of("contest:read")))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(read(result).path("code").asInt())
                .isEqualTo(ErrorCode.FUNCTION_FORBIDDEN.getCode());
        verifyNoInteractions(overviewService);
    }

    @Test
    void nonGlobalScopeGets403() throws Exception {
        when(overviewService.getCurrentOverview()).thenThrow(new BizException(
                ErrorCode.DATA_FORBIDDEN.getCode(),
                "非 GLOBAL 范围"
        ));

        MvcResult result = mockMvc.perform(get("/api/v1/dashboard/overview")
                        .with(authentication(dashboardAuthentication(Set.of("dashboard:view")))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(read(result).path("code").asInt()).isEqualTo(ErrorCode.DATA_FORBIDDEN.getCode());
    }

    @Test
    void administratorCanCreateTicket() throws Exception {
        when(ticketService.createCurrentTicket()).thenReturn(new DashboardTicketVO(
                "one-time-ticket",
                OffsetDateTime.parse("2026-11-21T17:30:30+08:00")
        ));

        MvcResult result = mockMvc.perform(post("/api/v1/dashboard/ws-ticket")
                        .with(authentication(dashboardAuthentication(Set.of("dashboard:view"))))
                        .with(csrf()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = read(result);
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").path("ticket").asText()).isEqualTo("one-time-ticket");
        assertThat(body.path("data").path("expires_at").asText())
                .isEqualTo("2026-11-21T17:30:30+08:00");
    }

    @Test
    void redisFailureReturns503And50003() throws Exception {
        when(ticketService.createCurrentTicket()).thenThrow(new BizException(
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                "Redis 不可用"
        ));

        MvcResult result = mockMvc.perform(post("/api/v1/dashboard/ws-ticket")
                        .with(authentication(dashboardAuthentication(Set.of("dashboard:view"))))
                        .with(csrf()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(read(result).path("code").asInt())
                .isEqualTo(ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode());
    }

    private DashboardOverviewVO overview() {
        return new DashboardOverviewVO(
                OffsetDateTime.parse("2026-11-21T17:30:00+08:00"),
                DashboardOverviewService.DATA_SOURCE,
                new DashboardSummaryVO(4, 9, 6, 0),
                new DashboardContestOperationsVO(2, 6, List.of(
                        new DashboardLevelDistributionVO("SCHOOL", 3),
                        new DashboardLevelDistributionVO("PROVINCE", 2),
                        new DashboardLevelDistributionVO("NATIONAL", 1)
                )),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private Authentication dashboardAuthentication(Set<String> permissions) {
        AuthenticatedUser principal = new AuthenticatedUser(
                1L,
                "session-admin",
                Set.of("ADMIN"),
                permissions,
                Set.of("ALL")
        );
        return new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
            "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
    })
    @Import({
            DashboardController.class,
            DashboardAccessDeniedAdvice.class,
            SecurityConfig.class,
            SecurityCurrentUserProvider.class,
            SecurityErrorWriter.class,
            GlobalExceptionHandler.class,
            TraceIdFilter.class,
            CorsConfig.class
    })
    static class TestApplication {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        DashboardOverviewService dashboardOverviewService() {
            return mock(DashboardOverviewService.class);
        }

        @Bean
        DashboardTicketService dashboardTicketService() {
            return mock(DashboardTicketService.class);
        }

        @Bean
        JwtService jwtService() {
            return mock(JwtService.class);
        }

        @Bean
        AuthSessionService authSessionService() {
            return mock(AuthSessionService.class);
        }
    }
}
