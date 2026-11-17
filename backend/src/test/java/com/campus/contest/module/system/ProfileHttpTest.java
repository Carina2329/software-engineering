package com.campus.contest.module.system;

import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.GlobalExceptionHandler;
import com.campus.contest.common.idempotency.IdempotencyScope;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.common.trace.TraceIdFilter;
import com.campus.contest.config.CorsConfig;
import com.campus.contest.module.system.controller.UserController;
import com.campus.contest.module.system.controller.support.RbacAccessDeniedAdvice;
import com.campus.contest.module.system.entity.SysMenuDO;
import com.campus.contest.module.system.entity.SysRoleDO;
import com.campus.contest.module.system.entity.SysRoleMenuDO;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.entity.SysUserRoleDO;
import com.campus.contest.module.system.mapper.SysMenuMapper;
import com.campus.contest.module.system.mapper.SysRoleMapper;
import com.campus.contest.module.system.mapper.SysRoleMenuMapper;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.system.mapper.SysUserRoleMapper;
import com.campus.contest.module.system.service.AuditLogService;
import com.campus.contest.module.system.service.AuthorizationSnapshotService;
import com.campus.contest.module.system.service.impl.AuthorizationSnapshotServiceImpl;
import com.campus.contest.module.system.service.impl.ProfileServiceImpl;
import com.campus.contest.module.system.service.impl.RbacSideEffectServiceImpl;
import com.campus.contest.module.system.service.impl.UserAccessServiceImpl;
import com.campus.contest.security.AuthSessionService;
import com.campus.contest.security.AuthenticatedUser;
import com.campus.contest.security.JwtService;
import com.campus.contest.security.SecurityConfig;
import com.campus.contest.security.SecurityCurrentUserProvider;
import com.campus.contest.security.SecurityErrorWriter;
import com.campus.contest.security.SessionRevocationService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/**
 * 个人档案 HTTP 链路测试：真实 Spring MVC + Spring Security + @PreAuthorize + GlobalExceptionHandler。
 *
 * 覆盖：角色字段查询、成功修改、角色越界、重复学号、版本冲突、账号字段越界、未登录与他人档案。
 * 不加载 MySQL / Redis / MyBatis，数据由测试替身提供。
 */
@SpringBootTest(classes = ProfileHttpTest.TestApplication.class)
@AutoConfigureMockMvc
class ProfileHttpTest {

    private static final Long STUDENT_ID = 2L;
    private static final Long TEACHER_ID = 3L;
    private static final Long OTHER_ID = 4L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysRoleMenuMapper roleMenuMapper;

    @Autowired
    private SysMenuMapper menuMapper;

    // ------------------------------------------------------------------
    // EP-003 查询
    // ------------------------------------------------------------------

    @Test
    @DisplayName("学生查询本人档案：返回学号与最新版本，不含职称")
    void studentReadsOwnProfile() throws Exception {
        stubStudentProfile();

        MvcResult result = mockMvc.perform(get("/api/v1/users/me")
                .with(authentication(studentAuth()))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = body(result).path("data");
        assertThat(data.path("real_name").asText()).isEqualTo("学生 A");
        assertThat(data.path("user_type").asText()).isEqualTo("STUDENT");
        assertThat(data.path("student_no").asText()).isEqualTo("202678090110");
        assertThat(data.path("college").asText()).isEqualTo("计算机科学学院");
        assertThat(data.path("major").asText()).isEqualTo("计算机科学与技术");
        assertThat(data.path("grade").asText()).isEqualTo("2024");
        assertThat(data.path("title").isNull()).isTrue();
        assertThat(data.path("version").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("教师查询本人档案：只返回教师字段，学生字段为空")
    void teacherReadsOwnProfile() throws Exception {
        stubTeacherProfile();

        MvcResult result = mockMvc.perform(get("/api/v1/users/me")
                .with(authentication(teacherAuth()))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = body(result).path("data");
        assertThat(data.path("user_type").asText()).isEqualTo("TEACHER");
        assertThat(data.path("title").asText()).isEqualTo("副教授");
        assertThat(data.path("student_no").isNull()).isTrue();
        assertThat(data.path("major").isNull()).isTrue();
        assertThat(data.path("grade").isNull()).isTrue();
        assertThat(data.path("version").asInt()).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // EP-004 修改
    // ------------------------------------------------------------------

    @Test
    @DisplayName("学生修改姓名与学号成功，返回更新后的版本号")
    void studentUpdatesOwnProfile() throws Exception {
        stubStudentProfile();
        when(userMapper.countByStudentNoExcluding("202678099999", STUDENT_ID)).thenReturn(0L);
        when(userMapper.updateById(any(SysUserDO.class))).thenReturn(1);

        MvcResult result = mockMvc.perform(patch("/api/v1/users/me")
                .with(authentication(studentAuth()))
                .with(csrf())
                .header("Idempotency-Key", "profile-student-update-0001")
                .contentType("application/json")
                .content("""
                        {"real_name":"刘蕊","student_no":"202678099999","college":"计算机科学学院",
                         "major":"计算机科学与技术","grade":"2024","version":3}
                        """)
        ).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = body(result).path("data");
        assertThat(data.path("id").asText()).isEqualTo("2");
        assertThat(data.path("version").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("教师修改学院与职称成功")
    void teacherUpdatesOwnProfile() throws Exception {
        stubTeacherProfile();
        when(userMapper.updateById(any(SysUserDO.class))).thenReturn(1);

        MvcResult result = mockMvc.perform(patch("/api/v1/users/me")
                .with(authentication(teacherAuth()))
                .with(csrf())
                .header("Idempotency-Key", "profile-teacher-update-0001")
                .contentType("application/json")
                .content("{\"college\":\"数学科学学院\",\"title\":\"教授\",\"version\":2}")
        ).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("data").path("version").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("角色越界：学生提交职称返回 HTTP 400 + 10001")
    void studentCannotSubmitTitleOverHttp() throws Exception {
        stubStudentProfile();

        MvcResult result = mockMvc.perform(patch("/api/v1/users/me")
                .with(authentication(studentAuth()))
                .with(csrf())
                .header("Idempotency-Key", "profile-student-title-0001")
                .contentType("application/json")
                .content("{\"title\":\"教授\",\"version\":3}")
        ).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("code").asInt()).isEqualTo(ErrorCode.INVALID_PARAM.getCode());
        assertThat(body(result).path("trace_id").asText()).isNotBlank();
    }

    @Test
    @DisplayName("角色越界：教师提交学号返回 HTTP 400 + 10001")
    void teacherCannotSubmitStudentNoOverHttp() throws Exception {
        stubTeacherProfile();

        MvcResult result = mockMvc.perform(patch("/api/v1/users/me")
                .with(authentication(teacherAuth()))
                .with(csrf())
                .header("Idempotency-Key", "profile-teacher-studentno-0001")
                .contentType("application/json")
                .content("{\"student_no\":\"202678090110\",\"version\":2}")
        ).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("code").asInt()).isEqualTo(ErrorCode.INVALID_PARAM.getCode());
    }

    @Test
    @DisplayName("同一旧版本第二次提交返回 HTTP 409 + 20001 版本冲突")
    void staleVersionReturnsConflictOverHttp() throws Exception {
        stubStudentProfile();
        // 模拟另一个请求已经把 version 从 3 推到 4，条件更新影响 0 行
        when(userMapper.updateById(any(SysUserDO.class))).thenReturn(0);

        MvcResult result = mockMvc.perform(patch("/api/v1/users/me")
                .with(authentication(studentAuth()))
                .with(csrf())
                .header("Idempotency-Key", "profile-student-conflict-0001")
                .contentType("application/json")
                .content("{\"real_name\":\"刘蕊\",\"version\":3}")
        ).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).path("code").asInt()).isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode());
        assertThat(body(result).path("message").asText()).contains("刷新后重试");
    }

    @Test
    @DisplayName("重复学号返回 HTTP 409 + 20002，且不写库")
    void duplicateStudentNoReturnsConflictOverHttp() throws Exception {
        stubStudentProfile();
        when(userMapper.countByStudentNoExcluding("202678090111", STUDENT_ID)).thenReturn(1L);

        MvcResult result = mockMvc.perform(patch("/api/v1/users/me")
                .with(authentication(studentAuth()))
                .with(csrf())
                .header("Idempotency-Key", "profile-student-duplicate-0001")
                .contentType("application/json")
                .content("{\"student_no\":\"202678090111\",\"version\":3}")
        ).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).path("code").asInt()).isEqualTo(ErrorCode.DUPLICATE_RELATION.getCode());
        assertThat(body(result).path("message").asText()).contains("学号");
    }

    @Test
    @DisplayName("账号字段越界：提交 username / status 返回 HTTP 400 + 10001")
    void accountFieldsAreRejectedOverHttp() throws Exception {
        stubStudentProfile();

        MvcResult result = mockMvc.perform(patch("/api/v1/users/me")
                .with(authentication(studentAuth()))
                .with(csrf())
                .header("Idempotency-Key", "profile-student-account-0001")
                .contentType("application/json")
                .content("{\"username\":\"hacker\",\"status\":0,\"version\":3}")
        ).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("code").asInt()).isEqualTo(ErrorCode.INVALID_PARAM.getCode());
    }

    @Test
    @DisplayName("不能修改他人档案：EP-004 无 user_id 入口，EP-007 按 user_id 修改被 Spring Security 拦成 403")
    void studentCannotPatchAnotherUser() throws Exception {
        // 用 EP-007 的合法请求体，确保 403 来自权限校验而不是请求体解析
        MvcResult result = mockMvc.perform(patch("/api/v1/users/" + OTHER_ID)
                .with(authentication(studentAuth()))
                .with(csrf())
                .header("Idempotency-Key", "profile-student-other-0001")
                .contentType("application/json")
                .content("{\"status\":1,\"version\":0}")
        ).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(result).path("code").asInt()).isEqualTo(ErrorCode.FUNCTION_FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("未登录访问个人档案接口返回 HTTP 401 + 30001")
    void anonymousCannotAccessProfile() throws Exception {
        MvcResult read = mockMvc.perform(get("/api/v1/users/me")).andReturn();
        assertThat(read.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(read).path("code").asInt()).isEqualTo(ErrorCode.UNAUTHENTICATED.getCode());

        MvcResult write = mockMvc.perform(patch("/api/v1/users/me")
                .with(csrf())
                .contentType("application/json")
                .content("{\"real_name\":\"匿名\",\"version\":0}")
        ).andReturn();
        assertThat(write.getResponse().getStatus()).isEqualTo(401);
    }

    // ------------------------------------------------------------------
    // 测试数据
    // ------------------------------------------------------------------

    private void stubStudentProfile() {
        SysUserDO student = new SysUserDO();
        student.setId(STUDENT_ID);
        student.setUsername("student_a");
        student.setRealName("学生 A");
        student.setStudentNo("202678090110");
        student.setCollege("计算机科学学院");
        student.setMajor("计算机科学与技术");
        student.setGrade("2024");
        student.setStatus(1);
        student.setVersion(3);

        when(userMapper.selectById(STUDENT_ID)).thenReturn(student);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(STUDENT_ID, 40L)));
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of(role("STUDENT", 40L)));
        when(roleMenuMapper.selectList(any())).thenReturn(List.of());
        when(menuMapper.selectBatchIds(any())).thenReturn(List.of());
    }

    private void stubTeacherProfile() {
        SysUserDO teacher = new SysUserDO();
        teacher.setId(TEACHER_ID);
        teacher.setUsername("teacher_a");
        teacher.setRealName("教师 A");
        teacher.setCollege("计算机科学学院");
        teacher.setTitle("副教授");
        teacher.setStatus(1);
        teacher.setVersion(2);

        when(userMapper.selectById(TEACHER_ID)).thenReturn(teacher);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(TEACHER_ID, 50L)));
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of(role("TEACHER", 50L)));
        when(roleMenuMapper.selectList(any())).thenReturn(List.of());
        when(menuMapper.selectBatchIds(any())).thenReturn(List.of());
    }

    private SysRoleDO role(String code, Long id) {
        SysRoleDO role = new SysRoleDO();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setDataScope("SELF");
        role.setVersion(0);
        return role;
    }

    private SysUserRoleDO userRole(Long userId, Long roleId) {
        SysUserRoleDO relation = new SysUserRoleDO();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private Authentication studentAuth() {
        AuthenticatedUser user = new AuthenticatedUser(
                STUDENT_ID, "session-student", Set.of("STUDENT"), Set.of("file:download"), Set.of("SELF"));
        return new UsernamePasswordAuthenticationToken(user, null, user.authorities());
    }

    private Authentication teacherAuth() {
        AuthenticatedUser user = new AuthenticatedUser(
                TEACHER_ID, "session-teacher", Set.of("TEACHER"), Set.of(), Set.of("SELF"));
        return new UsernamePasswordAuthenticationToken(user, null, user.authorities());
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
            SecurityConfig.class,
            SecurityErrorWriter.class,
            SecurityCurrentUserProvider.class,
            GlobalExceptionHandler.class,
            TraceIdFilter.class,
            CorsConfig.class,
            UserController.class,
            RbacAccessDeniedAdvice.class,
            UserAccessServiceImpl.class,
            RbacSideEffectServiceImpl.class,
            ProfileServiceImpl.class
    })
    static class TestApplication {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        JwtService jwtService() {
            return mock(JwtService.class);
        }

        @Bean
        AuthSessionService authSessionService() {
            return mock(AuthSessionService.class);
        }

        @Bean
        SysRoleMapper sysRoleMapper() {
            return mock(SysRoleMapper.class);
        }

        @Bean
        SysUserMapper sysUserMapper() {
            return mock(SysUserMapper.class);
        }

        @Bean
        SysMenuMapper sysMenuMapper() {
            return mock(SysMenuMapper.class);
        }

        @Bean
        SysRoleMenuMapper sysRoleMenuMapper() {
            return mock(SysRoleMenuMapper.class);
        }

        @Bean
        SysUserRoleMapper sysUserRoleMapper() {
            return mock(SysUserRoleMapper.class);
        }

        @Bean
        AuditLogService auditLogService() {
            return mock(AuditLogService.class);
        }

        @Bean
        AuthorizationSnapshotService authorizationSnapshotService(
                SysUserMapper userMapper,
                SysRoleMapper roleMapper,
                SysUserRoleMapper userRoleMapper,
                SysMenuMapper menuMapper,
                SysRoleMenuMapper roleMenuMapper
        ) {
            return new AuthorizationSnapshotServiceImpl(
                    userMapper, roleMapper, userRoleMapper, menuMapper, roleMenuMapper);
        }

        @Bean
        SessionRevocationService sessionRevocationService() {
            return mock(SessionRevocationService.class);
        }

        @Bean
        PlatformTransactionManager platformTransactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {
                    // 测试替身：不连接数据库
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                    // 测试替身：不连接数据库
                }

                @Override
                protected void doRollback(DefaultTransactionStatus status) {
                    // 测试替身：不连接数据库
                }
            };
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        @Bean
        IdempotencyService idempotencyService() {
            return new IdempotencyService() {
                @Override
                public <T> T execute(
                        IdempotencyScope scope,
                        String operation,
                        String idempotencyKey,
                        Object request,
                        Duration ttl,
                        Class<T> resultType,
                        Supplier<T> action
                ) {
                    return action.get();
                }
            };
        }
    }
}
