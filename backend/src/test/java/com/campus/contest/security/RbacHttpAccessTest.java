package com.campus.contest.security;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyScope;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.common.trace.TraceIdFilter;
import com.campus.contest.config.CorsConfig;
import com.campus.contest.module.system.controller.PermissionResourceController;
import com.campus.contest.module.system.controller.RoleController;
import com.campus.contest.module.system.controller.UserController;
import com.campus.contest.module.system.entity.SysMenuDO;
import com.campus.contest.module.system.entity.SysRoleDO;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.mapper.SysMenuMapper;
import com.campus.contest.module.system.mapper.SysRoleMapper;
import com.campus.contest.module.system.mapper.SysRoleMenuMapper;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.system.mapper.SysUserRoleMapper;
import com.campus.contest.module.system.service.AuditLogService;
import com.campus.contest.module.system.service.impl.PermissionResourceServiceImpl;
import com.campus.contest.module.system.service.impl.RbacSideEffectServiceImpl;
import com.campus.contest.module.system.service.impl.RoleServiceImpl;
import com.campus.contest.module.system.service.impl.UserAccessServiceImpl;
import com.campus.contest.module.work.controller.FileController;
import com.campus.contest.module.work.entity.FileDO;
import com.campus.contest.module.work.service.FileService;
import com.campus.contest.module.work.service.impl.FileAccessGuardImpl;
import com.campus.contest.storage.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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

import java.io.ByteArrayInputStream;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 真实 Spring MVC + Spring Security + @PreAuthorize 的越权链路测试。
 *
 * 覆盖两类越权：
 * 1. 垂直越权：学生访问管理员接口，必须 HTTP 403 + 30003；
 * 2. 水平越权：学生 A 下载学生 B 的文件，必须 HTTP 404 + 40404，不泄露文件是否存在。
 *
 * 不加载 MySQL、Redis 和 MyBatis，数据由测试替身提供。
 */
@EnabledIfEnvironmentVariable(named = "RUN_RBAC_HTTP_TESTS", matches = "true")
@SpringBootTest(classes = RbacHttpAccessTest.TestApplication.class)
@AutoConfigureMockMvc
class RbacHttpAccessTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long STUDENT_A_ID = 2L;
    private static final Long STUDENT_B_ID = 3L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysMenuMapper menuMapper;

    @Autowired
    private SysRoleMenuMapper roleMenuMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private FileService fileService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private SessionRevocationService sessionRevocationService;

    @Test
    @DisplayName("垂直越权：学生访问角色管理接口返回 HTTP 403")
    void studentCannotReadRoleManagement() throws Exception {
        SysRoleDO adminRole = role("ADMIN", 1L, 6, "ALL");
        when(roleMapper.selectList(any())).thenReturn(List.of(adminRole));

        MvcResult result = mockMvc.perform(get("/api/v1/roles")
                .with(authentication(studentAAuth()))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("code").asInt()).isEqualTo(ErrorCode.FUNCTION_FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("管理员可以查询角色列表，返回 200 与标准 Result")
    void adminCanReadRoleManagement() throws Exception {
        SysRoleDO adminRole = role("ADMIN", 1L, 6, "ALL");
        when(roleMapper.selectList(any())).thenReturn(List.of(adminRole));

        MvcResult result = mockMvc.perform(get("/api/v1/roles")
                .with(authentication(adminAuth()))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").get(0).path("role_code").asText()).isEqualTo("ADMIN");
        assertThat(body.path("data").get(0).path("data_scope").asText()).isEqualTo("ALL");
    }

    @Test
    @DisplayName("垂直越权：学生访问用户管理接口返回 HTTP 403")
    void studentCannotReadUserManagement() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users")
                .with(authentication(studentAAuth()))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("code").asInt()).isEqualTo(ErrorCode.FUNCTION_FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("学生可以读取自己的菜单树，但不能读取全量权限资源树")
    void currentUserMenuTreeIsReadableForStudent() throws Exception {
        SysMenuDO filesMenu = menu(30L, null, "文件中心", 2, "/files", "file:download");
        when(menuMapper.selectList(any())).thenReturn(List.of(filesMenu));
        when(roleMenuMapper.selectList(any())).thenReturn(List.of());
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        MvcResult current = mockMvc.perform(get("/api/v1/menus/current")
                .with(authentication(studentAAuth()))).andReturn();
        assertThat(current.getResponse().getStatus()).isEqualTo(200);

        MvcResult tree = mockMvc.perform(get("/api/v1/menus")
                .with(authentication(studentAAuth()))).andReturn();
        assertThat(tree.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("水平越权：学生 A 下载学生 B 的文件返回 HTTP 404")
    void studentCannotDownloadOthersFile() throws Exception {
        FileDO fileOfB = file(9L, STUDENT_B_ID);
        when(fileService.getById(9L)).thenReturn(fileOfB);
        when(storageService.open(any())).thenReturn(new ByteArrayInputStream("PDF".getBytes()));

        MvcResult result = mockMvc.perform(get("/api/v1/files/9/content")
                .with(authentication(studentAAuth()))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("code").asInt()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("学生 A 下载本人文件成功，管理员可以查看任意用户文件")
    void ownFileIsDownloadable() throws Exception {
        FileDO ownFile = file(10L, STUDENT_A_ID);
        when(fileService.getById(10L)).thenReturn(ownFile);
        when(storageService.open(any())).thenReturn(new ByteArrayInputStream("PDF".getBytes()));

        MvcResult student = mockMvc.perform(get("/api/v1/files/10/content")
                .with(authentication(studentAAuth()))).andReturn();
        assertThat(student.getResponse().getStatus()).isEqualTo(200);

        MvcResult admin = mockMvc.perform(get("/api/v1/files/10/content")
                .with(authentication(adminAuth()))).andReturn();
        assertThat(admin.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("缺少 Idempotency-Key 的写请求返回 400，带 key 的学生请求仍返回 403")
    void writeRequestsRequireIdempotencyKeyAndPermission() throws Exception {
        MvcResult missingKey = mockMvc.perform(post("/api/v1/roles")
                .with(authentication(adminAuth()))
                .with(csrf())
                .contentType("application/json")
                .content("{\"role_code\":\"TEST_ROLE\",\"role_name\":\"测试角色\",\"data_scope\":\"SELF\"}")
        ).andReturn();
        assertThat(missingKey.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(missingKey.getResponse().getContentAsString())
                .path("code").asInt()).isEqualTo(ErrorCode.INVALID_PARAM.getCode());

        MvcResult studentWrite = mockMvc.perform(put("/api/v1/roles/1/menus")
                .with(authentication(studentAAuth()))
                .with(csrf())
                .header("Idempotency-Key", "rbac-test-key")
                .contentType("application/json")
                .content("{\"menu_ids\":[\"30\"],\"version\":1}")
        ).andReturn();
        assertThat(studentWrite.getResponse().getStatus()).isEqualTo(403);
        assertThat(objectMapper.readTree(studentWrite.getResponse().getContentAsString())
                .path("code").asInt()).isEqualTo(ErrorCode.FUNCTION_FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("管理员可以查询用户列表，分页字段与契约一致")
    void adminCanReadUserPage() throws Exception {
        SysUserDO student = new SysUserDO();
        student.setId(STUDENT_A_ID);
        student.setUsername("student_a");
        student.setRealName("学生 A");
        student.setStatus(1);
        student.setVersion(0);

        IPage<SysUserDO> page = new Page<>(1, 20);
        page.setRecords(List.of(student));
        page.setTotal(1);
        when(userMapper.selectPage(any(), any())).thenReturn(page);
        when(userRoleMapper.selectList(any())).thenReturn(List.of());
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of());

        MvcResult result = mockMvc.perform(get("/api/v1/users")
                .param("page", "1")
                .param("size", "20")
                .with(authentication(adminAuth()))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("data").path("total").asLong()).isEqualTo(1L);
        assertThat(body.path("data").path("has_more").asBoolean()).isFalse();
        assertThat(body.path("data").path("items").get(0).path("username").asText())
                .isEqualTo("student_a");
    }

    // ------------------------------------------------------------------
    // 测试数据与认证构造
    // ------------------------------------------------------------------

    private SysRoleDO role(String code, Long id, Integer version, String dataScope) {
        SysRoleDO role = new SysRoleDO();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setDataScope(dataScope);
        role.setVersion(version);
        return role;
    }

    private SysMenuDO menu(Long id, Long parentId, String name, Integer type, String path, String perms) {
        SysMenuDO menu = new SysMenuDO();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setMenuName(name);
        menu.setMenuType(type);
        menu.setPath(path);
        menu.setPerms(perms);
        menu.setSortNo(1);
        menu.setVisible(1);
        menu.setStatus(1);
        return menu;
    }

    private FileDO file(Long id, Long uploaderId) {
        FileDO file = new FileDO();
        file.setId(id);
        file.setUploaderId(uploaderId);
        file.setFileSize(12L);
        file.setStoragePath("files/2026/09/demo.pdf");
        file.setOriginalName("demo.pdf");
        file.setMimeType("application/pdf");
        file.setDeleted(0);
        return file;
    }

    private Authentication adminAuth() {
        AuthenticatedUser user = new AuthenticatedUser(
                ADMIN_ID,
                "session-admin",
                Set.of("ADMIN"),
                Set.of(
                        "system:user:list", "system:user:update",
                        "system:role:list", "system:role:create", "system:role:update",
                        "system:role:authorize", "system:menu:list",
                        "file:download", "file:upload"
                ),
                Set.of("ALL")
        );
        return new UsernamePasswordAuthenticationToken(user, null, user.authorities());
    }

    private Authentication studentAAuth() {
        AuthenticatedUser user = new AuthenticatedUser(
                STUDENT_A_ID,
                "session-a",
                Set.of("STUDENT"),
                Set.of("file:download", "file:upload"),
                Set.of("SELF")
        );
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
            com.campus.contest.common.GlobalExceptionHandler.class,
            TraceIdFilter.class,
            CorsConfig.class,
            RoleController.class,
            PermissionResourceController.class,
            UserController.class,
            com.campus.contest.module.system.controller.support.RbacAccessDeniedAdvice.class,
            FileController.class,
            RoleServiceImpl.class,
            PermissionResourceServiceImpl.class,
            UserAccessServiceImpl.class,
            RbacSideEffectServiceImpl.class,
            FileAccessGuardImpl.class
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
        SessionRevocationService sessionRevocationService() {
            return mock(SessionRevocationService.class);
        }

        @Bean
        FileService fileService() {
            return mock(FileService.class);
        }

        @Bean
        StorageService storageService() {
            return mock(StorageService.class);
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
