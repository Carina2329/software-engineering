package com.campus.contest.security;

import com.campus.contest.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * 无状态 Spring Security 认证链。受保护请求必须同时通过 JWT 和 Redis 会话校验。
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityErrorWriter errorWriter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");
        return repository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CsrfTokenRepository csrfTokenRepository,
            JwtService jwtService,
            AuthSessionService sessionService
    ) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(jwtService, sessionService, errorWriter);
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        // Bearer JWT 每个请求都会重新建立 Authentication。
                        // 默认策略会把它当成一次登录并删除 XSRF Cookie，导致下一次
                        // Refresh 请求缺少 CSRF Token。无状态 JWT 不需要这种轮换。
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())
                        .ignoringRequestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/email-verification-codes",
                                "/api/v1/auth/register",
                                "/api/v1/auth/password-reset/request",
                                "/api/v1/auth/password-reset/confirm"
                        )
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/doc.html",
                                "/webjars/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, ex) ->
                                errorWriter.write(response, ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, ex) ->
                                errorWriter.write(response, ErrorCode.FUNCTION_FORBIDDEN))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
