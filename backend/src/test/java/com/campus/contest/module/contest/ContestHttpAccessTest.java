package com.campus.contest.module.contest;

import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.GlobalExceptionHandler;
import com.campus.contest.common.trace.TraceIdFilter;
import com.campus.contest.config.CorsConfig;
import com.campus.contest.module.contest.controller.ContestController;
import com.campus.contest.module.contest.controller.EditionController;
import com.campus.contest.module.contest.controller.MilestoneController;
import com.campus.contest.module.contest.controller.support.ContestAccessDeniedAdvice;
import com.campus.contest.module.contest.service.ContestService;
import com.campus.contest.module.contest.service.EditionService;
import com.campus.contest.module.contest.service.MilestoneService;
import com.campus.contest.module.contest.vo.ContestCreateVO;
import com.campus.contest.module.contest.vo.ContestDetailVO;
import com.campus.contest.module.contest.vo.EditionDetailVO;
import com.campus.contest.module.contest.vo.MilestoneVO;
import com.campus.contest.security.AuthSessionService;
import com.campus.contest.security.AuthenticatedUser;
import com.campus.contest.security.JwtService;
import com.campus.contest.security.SecurityConfig;
import com.campus.contest.security.SecurityCurrentUserProvider;
import com.campus.contest.security.SecurityErrorWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(classes = ContestHttpAccessTest.TestApplication.class)
@AutoConfigureMockMvc
class ContestHttpAccessTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ContestService contestService;
    @Autowired
    private EditionService editionService;
    @Autowired
    private MilestoneService milestoneService;

    @Test
    @DisplayName("EP-012 管理员创建竞赛返回 201 与标准 Result")
    void managerCanCreateContest() throws Exception {
        when(contestService.create(any(), eq("contest-http-key")))
                .thenReturn(new ContestCreateVO("100", "测试竞赛"));

        MvcResult result = mockMvc.perform(post("/api/v1/contests")
                        .with(authentication(managerAuth()))
                        .with(csrf())
                        .header("Idempotency-Key", "contest-http-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contest_name": "测试竞赛",
                                  "series_code": "TEST_SERIES",
                                  "level": "SCHOOL",
                                  "organizer": "计算机学院",
                                  "intro": "用于 HTTP 测试"
                                }
                                """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").path("contest_name").asText()).isEqualTo("测试竞赛");
        assertThat(body.path("trace_id").asText()).isNotBlank();
    }

    @Test
    @DisplayName("普通用户调用管理员创建接口返回 403 + 30003")
    void ordinaryUserCannotCreateContest() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/contests")
                        .with(authentication(studentAuth()))
                        .with(csrf())
                        .header("Idempotency-Key", "forbidden-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contest_name": "越权竞赛",
                                  "series_code": "FORBIDDEN_SERIES",
                                  "level": "SCHOOL"
                                }
                                """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(objectMapper.readTree(
                        result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("code").asInt()).isEqualTo(ErrorCode.FUNCTION_FORBIDDEN.getCode());
        verify(contestService, never()).create(any(), any());
    }

    @Test
    @DisplayName("管理员缺少 Idempotency-Key 返回 HTTP 400 + 10001")
    void idempotencyKeyIsRequiredForWrites() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/contests")
                        .with(authentication(managerAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contest_name": "缺少幂等键",
                                  "series_code": "NO_KEY_SERIES",
                                  "level": "SCHOOL"
                                }
                                """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(
                        result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("code").asInt()).isEqualTo(ErrorCode.INVALID_PARAM.getCode());
    }

    @Test
    @DisplayName("EP-014 无 Idempotency-Key 时由 version 兜底并正常进入 Service")
    void optionalIdempotencyKeyFallsBackToVersion() throws Exception {
        when(contestService.update(eq(1L), any(), isNull()))
                .thenReturn(new com.campus.contest.module.contest.vo.ContestVersionVO("1", 2));

        MvcResult result = mockMvc.perform(patch("/api/v1/contests/1")
                        .with(authentication(managerAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizer": "测试主办方",
                                  "version": 1
                                }
                                """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(
                        result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("version").asInt()).isEqualTo(2);
        verify(contestService).update(eq(1L), any(), isNull());
    }

    @Test
    @DisplayName("普通用户可读取已发布届次窗口与只读状态")
    void ordinaryUserCanReadPublishedEditionWindow() throws Exception {
        OffsetDateTime deadline = OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(3);
        when(editionService.get(31L)).thenReturn(new EditionDetailVO(
                "31",
                "1",
                "2026 年校赛",
                2026,
                "REGISTERING",
                "NO_TOPIC",
                true,
                "REVIEW",
                1,
                deadline,
                deadline.plusDays(2),
                2,
                5,
                0,
                "RANGE",
                3,
                deadline.plusDays(3),
                deadline.plusDays(4),
                deadline.plusDays(5),
                true,
                true,
                false,
                "10",
                "测试学校",
                "SCHOOL",
                "10",
                null,
                "SCHOOL",
                "OPEN",
                false
        ));

        MvcResult result = mockMvc.perform(get("/api/v1/editions/31")
                        .with(authentication(studentAuth())))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = objectMapper.readTree(
                        result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertThat(data.path("registration_open").asBoolean()).isTrue();
        assertThat(data.path("submission_open").asBoolean()).isTrue();
        assertThat(data.path("read_only").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("EP-013 响应包含竞赛 version")
    void contestDetailResponseContainsVersion() throws Exception {
        when(contestService.get(1L))
                .thenReturn(new ContestDetailVO(
                        "1",
                        "测试竞赛",
                        "SCHOOL",
                        "计算机学院",
                        "简介",
                        7,
                        List.of("2026 年校赛"),
                        "TEST_SERIES"
                ));

        MvcResult result = mockMvc.perform(get("/api/v1/contests/1")
                        .with(authentication(managerAuth())))
                .andReturn();

        JsonNode data = objectMapper.readTree(
                        result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertThat(data.path("version").asInt()).isEqualTo(7);
        assertThat(data.path("contest_name").asText()).isEqualTo("测试竞赛");
    }

    @Test
    @DisplayName("EP-020 每个赛程节点响应包含 version")
    void milestoneListResponseContainsVersion() throws Exception {
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(1);
        when(milestoneService.list(31L)).thenReturn(List.of(
                new MilestoneVO(
                        "51",
                        3,
                        "WORK_END",
                        "作品提交结束",
                        start,
                        start.plusHours(1),
                        true
                )
        ));

        MvcResult result = mockMvc.perform(get("/api/v1/editions/31/milestones")
                        .with(authentication(studentAuth())))
                .andReturn();

        JsonNode item = objectMapper.readTree(
                        result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .get(0);
        assertThat(item.path("version").asInt()).isEqualTo(3);
        assertThat(item.path("node_name").asText()).isEqualTo("作品提交结束");
    }

    private Authentication managerAuth() {
        AuthenticatedUser principal = new AuthenticatedUser(
                1L,
                "session-manager",
                Set.of("ADMIN"),
                Set.of("contest:create", "contest:update", "edition:publish"),
                Set.of("ALL")
        );
        return new UsernamePasswordAuthenticationToken(
                principal, null, principal.authorities()
        );
    }

    private Authentication studentAuth() {
        AuthenticatedUser principal = new AuthenticatedUser(
                2L,
                "session-student",
                Set.of("STUDENT"),
                Set.of("contest:read"),
                Set.of("SELF")
        );
        return new UsernamePasswordAuthenticationToken(
                principal, null, principal.authorities()
        );
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
            ContestController.class,
            EditionController.class,
            MilestoneController.class,
            ContestAccessDeniedAdvice.class,
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
        ContestService contestService() {
            return mock(ContestService.class);
        }

        @Bean
        EditionService editionService() {
            return mock(EditionService.class);
        }

        @Bean
        MilestoneService milestoneService() {
            return mock(MilestoneService.class);
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
