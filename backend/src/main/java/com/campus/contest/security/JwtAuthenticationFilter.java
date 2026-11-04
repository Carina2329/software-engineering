package com.campus.contest.security;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * 解析 Bearer JWT，并在每个受保护请求上确认对应 Redis 会话仍有效。
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AuthSessionService sessionService;
    private final SecurityErrorWriter errorWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith(BEARER_PREFIX) || authorization.length() <= BEARER_PREFIX.length()) {
            SecurityContextHolder.clearContext();
            errorWriter.write(response, ErrorCode.TOKEN_EXPIRED_OR_REVOKED);
            return;
        }

        String rawToken = authorization.substring(BEARER_PREFIX.length()).trim();
        try {
            Jwt jwt = jwtService.decode(rawToken);
            String subject = jwt.getSubject();
            String sessionId = jwt.getClaimAsString("sid");
            if (subject == null || subject.isBlank() || sessionId == null || sessionId.isBlank()) {
                throw new JwtException("JWT 缺少会话标识");
            }

            Optional<AuthSession> session = sessionService.find(sessionId);
            if (session.isEmpty()
                    || !subject.equals(String.valueOf(session.get().userId()))
                    || (session.get().refreshExpiresAt() != null
                    && session.get().refreshExpiresAt().isBefore(java.time.Instant.now()))) {
                SecurityContextHolder.clearContext();
                errorWriter.write(response, ErrorCode.TOKEN_EXPIRED_OR_REVOKED);
                return;
            }

            AuthenticatedUser principal = session.get().toPrincipal();
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            errorWriter.write(response, ErrorCode.TOKEN_EXPIRED_OR_REVOKED);
        } catch (BizException e) {
            SecurityContextHolder.clearContext();
            errorWriter.write(response, e.getCode(), e.getMessage());
        }
    }
}
