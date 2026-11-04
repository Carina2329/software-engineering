package com.campus.contest.security;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.Result;
import com.campus.contest.common.idempotency.TestRedisIdempotencyConfig;
import com.campus.contest.common.trace.TraceIdFilter;
import com.campus.contest.config.CorsConfig;
import com.campus.contest.module.system.controller.AuthController;
import com.campus.contest.module.system.controller.TestController;
import com.campus.contest.module.system.dto.AuthorizationSnapshot;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.service.AuditLogService;
import com.campus.contest.module.system.service.AuthAuditService;
import com.campus.contest.module.system.service.AuthLoginService;
import com.campus.contest.module.system.service.AuthTokenService;
import com.campus.contest.module.system.service.AuthUserLookupService;
import com.campus.contest.module.system.service.AuthorizationSnapshotService;
import com.campus.contest.module.system.service.EmailOtpVerifier;
import com.campus.contest.module.system.service.LoginRateLimiter;
import com.campus.contest.module.system.enums.EmailOtpScene;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 真实 Spring MVC、Spring Security、Redis 的 HTTP 认证链路测试。
 * 不加载 MySQL 和 MyBatis，用户与权限数据由测试替身提供。
 */
@EnabledIfEnvironmentVariable(named = "RUN_AUTH_HTTP_TESTS", matches = "true")
@SpringBootTest(
        classes = AuthHttpEndToEndTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.auth.jwt.secret=0123456789abcdef0123456789abcdef",
                "app.auth.jwt.issuer=contest-agent-platform",
                "app.auth.jwt.access-token-ttl-seconds=900",
                "app.auth.jwt.refresh-token-ttl-seconds=3600",
                "app.auth.cookie.secure=false",
                "app.auth.cookie.same-site=Lax",
                "spring.data.redis.password=${SPRING_DATA_REDIS_PASSWORD:}"
        }
)
class AuthHttpEndToEndTest {

    private static final String PASSWORD = "StudentA#2026";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthUserLookupService userLookupService;

    @Autowired
    private AuthorizationSnapshotService snapshotService;

    private String username;
    private Long userId;

    @BeforeEach
    void prepareUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        username = "http_" + suffix;
        userId = 1000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000L);

        SysUserDO user = new SysUserDO();
        user.setId(userId);
        user.setUsername(username);
        user.setRealName("HTTP 测试用户");
        user.setEmail(username + "@example.edu");
        user.setPasswordHash(new BCryptPasswordEncoder(4).encode(PASSWORD));
        user.setStatus(1);
        when(userLookupService.findByUsername(username)).thenReturn(Optional.of(user));
        when(userLookupService.findByEmail(username + "@example.edu")).thenReturn(Optional.of(user));
        when(snapshotService.loadRequired(userId)).thenReturn(new AuthorizationSnapshot(
                userId,
                username,
                1,
                Set.of("STUDENT"),
                Set.of("competition:read"),
                Set.of("SELF")
        ));
    }

    @Test
    void loginProtectedRefreshLogoutAndIdempotentLogoutOverHttp() throws Exception {
        String loginJson = objectMapper.writeValueAsString(Map.of(
                "login_type", "USERNAME_PASSWORD",
                "username", username,
                "password", PASSWORD,
                "captcha_id", "captcha-http-1",
                "captcha_code", "AB12"
        ));
        ResponseEntity<String> login = postJson("/api/v1/auth/login", loginJson, new HttpHeaders());
        assertEquals(HttpStatus.OK, login.getStatusCode(), login.getBody());
        JsonNode loginBody = json(login);
        assertEquals(0, loginBody.path("code").asInt(), login.getBody());
        String accessToken = loginBody.path("data").path("access_token").asText();
        assertFalse(accessToken.isBlank());
        assertEquals("Bearer", loginBody.path("data").path("token_type").asText());

        Map<String, String> loginCookies = cookies(login.getHeaders());
        assertTrue(loginCookies.containsKey("refresh_token"));
        assertTrue(loginCookies.containsKey("XSRF-TOKEN"));

        ResponseEntity<String> unauthenticated = restTemplate.exchange(
                "/api/v1/test/ping",
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                String.class
        );
        assertEquals(HttpStatus.UNAUTHORIZED, unauthenticated.getStatusCode());
        assertEquals(ErrorCode.UNAUTHENTICATED.getCode(), json(unauthenticated).path("code").asInt());

        ResponseEntity<String> protectedResponse = getWithBearer("/api/v1/test-auth/whoami", accessToken);
        assertEquals(HttpStatus.OK, protectedResponse.getStatusCode(), protectedResponse.getBody());
        JsonNode whoami = json(protectedResponse).path("data");
        assertEquals(String.valueOf(userId), whoami.path("user_id").asText());
        assertFalse(whoami.path("session_id").asText().isBlank());

        HttpHeaders refreshHeaders = cookieHeaders(loginCookies);
        refreshHeaders.add("X-XSRF-TOKEN", loginCookies.get("XSRF-TOKEN"));
        ResponseEntity<String> refresh = restTemplate.exchange(
                "/api/v1/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(refreshHeaders),
                String.class
        );
        assertEquals(HttpStatus.OK, refresh.getStatusCode(), refresh.getBody());
        JsonNode refreshedBody = json(refresh);
        String refreshedAccess = refreshedBody.path("data").path("access_token").asText();
        assertFalse(refreshedAccess.isBlank());
        Map<String, String> refreshedCookies = cookies(refresh.getHeaders());
        assertTrue(refreshedCookies.containsKey("refresh_token"));
        assertTrue(refreshedCookies.containsKey("XSRF-TOKEN"));

        HttpHeaders logoutHeaders = cookieHeaders(refreshedCookies);
        logoutHeaders.add("X-XSRF-TOKEN", refreshedCookies.get("XSRF-TOKEN"));
        logoutHeaders.add("Idempotency-Key", "http-e2e-logout");
        ResponseEntity<String> logout = restTemplate.exchange(
                "/api/v1/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(logoutHeaders),
                String.class
        );
        assertEquals(HttpStatus.OK, logout.getStatusCode(), logout.getBody());

        ResponseEntity<String> afterLogout = getWithBearer("/api/v1/test-auth/whoami", refreshedAccess);
        assertEquals(HttpStatus.UNAUTHORIZED, afterLogout.getStatusCode(), afterLogout.getBody());
        assertEquals(ErrorCode.TOKEN_EXPIRED_OR_REVOKED.getCode(),
                json(afterLogout).path("code").asInt());

        ResponseEntity<String> duplicateLogout = restTemplate.exchange(
                "/api/v1/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(logoutHeaders),
                String.class
        );
        assertEquals(HttpStatus.OK, duplicateLogout.getStatusCode(), duplicateLogout.getBody());
    }


    @Test
    void logoutWithoutIdempotencyKeyReturnsBadRequest() throws Exception {
        String loginJson = objectMapper.writeValueAsString(Map.of(
                "login_type", "USERNAME_PASSWORD",
                "username", username,
                "password", PASSWORD,
                "captcha_id", "captcha-http-2",
                "captcha_code", "AB12"
        ));
        ResponseEntity<String> login = postJson("/api/v1/auth/login", loginJson, new HttpHeaders());
        assertEquals(HttpStatus.OK, login.getStatusCode(), login.getBody());

        Map<String, String> loginCookies = cookies(login.getHeaders());
        HttpHeaders logoutHeaders = cookieHeaders(loginCookies);
        logoutHeaders.add("X-XSRF-TOKEN", loginCookies.get("XSRF-TOKEN"));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(logoutHeaders),
                String.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), response.getBody());
        assertEquals(ErrorCode.INVALID_PARAM.getCode(), json(response).path("code").asInt());
    }
    private ResponseEntity<String> postJson(
            String path,
            String json,
            HttpHeaders headers
    ) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    private ResponseEntity<String> getWithBearer(String path, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        assertNotNull(response.getBody());
        return objectMapper.readTree(response.getBody());
    }

    private Map<String, String> cookies(HttpHeaders headers) {
        Map<String, String> result = new LinkedHashMap<>();
        List<String> values = headers.getOrDefault(HttpHeaders.SET_COOKIE, List.of());
        for (String value : values) {
            String pair = value.split(";", 2)[0];
            int separator = pair.indexOf('=');
            if (separator > 0) {
                result.put(pair.substring(0, separator), pair.substring(separator + 1));
            }
        }
        return result;
    }

    private HttpHeaders cookieHeaders(Map<String, String> cookies) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE,
                "refresh_token=" + cookies.get("refresh_token")
                        + "; XSRF-TOKEN=" + cookies.get("XSRF-TOKEN"));
        return headers;
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
            AuthController.class,
            AuthLoginService.class,
            AuthTokenService.class,
            AuthAuditService.class,
            AuthSessionService.class,
            AuthCookieService.class,
            JwtService.class,
            LoginRateLimiter.class,
            SessionTokenCodec.class,
            SecurityConfig.class,
            SecurityCurrentUserProvider.class,
            SecurityErrorWriter.class,
            com.campus.contest.common.GlobalExceptionHandler.class,
            TraceIdFilter.class,
            CorsConfig.class,
            TestController.class,
            TestRedisIdempotencyConfig.class,
            TestApplication.ProtectedController.class
    })
    static class TestApplication {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        LettuceConnectionFactory redisConnectionFactory(
                @org.springframework.beans.factory.annotation.Value("${spring.data.redis.host:127.0.0.1}") String host,
                @org.springframework.beans.factory.annotation.Value("${spring.data.redis.port:6379}") int port,
                @org.springframework.beans.factory.annotation.Value("${spring.data.redis.password:}") String password
        ) {
            RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);
            if (password != null && !password.isBlank()) {
                configuration.setPassword(password);
            }
            return new LettuceConnectionFactory(configuration);
        }

        @Bean
        StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
            return new StringRedisTemplate(connectionFactory);
        }

        @Bean
        AuthUserLookupService authUserLookupService() {
            return mock(AuthUserLookupService.class);
        }

        @Bean
        AuthorizationSnapshotService authorizationSnapshotService() {
            return mock(AuthorizationSnapshotService.class);
        }

        @Bean
        AuditLogService auditLogService() {
            return mock(AuditLogService.class);
        }

        @Bean
        LoginCaptchaVerifier loginCaptchaVerifier() {
            return (captchaId, captchaCode) -> {
                if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
                    throw new BizException(ErrorCode.UNAUTHENTICATED.getCode(), "账号或凭证不正确");
                }
            };
        }

        @Bean
        EmailOtpVerifier emailOtpVerifier() {
            return (challengeId, email, emailCode, scene) -> {
                if (scene != EmailOtpScene.LOGIN) {
                    throw new BizException(ErrorCode.UNAUTHENTICATED.getCode(), "账号或凭证不正确");
                }
            };
        }

        @RestController
        @RequestMapping("/api/v1/test-auth")
        static class ProtectedController {

            private final CurrentUserProvider currentUserProvider;

            ProtectedController(CurrentUserProvider currentUserProvider) {
                this.currentUserProvider = currentUserProvider;
            }

            @GetMapping("/whoami")
            Result<Map<String, Object>> whoami() {
                return Result.ok(Map.of(
                        "user_id", String.valueOf(currentUserProvider.requireUserId()),
                        "session_id", currentUserProvider.requireSessionId()
                ));
            }
        }
    }
}
