package com.campus.contest.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 签发和校验短期 Access JWT。JWT 只包含非敏感标识字段。
 */
@Component
public class JwtService {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final String issuer;
    private final long accessTokenTtlSeconds;

    public JwtService(
            @Value("${app.auth.jwt.secret:}") String secret,
            @Value("${app.auth.jwt.issuer:contest-agent-platform}") String issuer,
            @Value("${app.auth.jwt.access-token-ttl-seconds:900}") long accessTokenTtlSeconds
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("AUTH_JWT_SECRET 必须配置且不能为空");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("AUTH_JWT_SECRET 长度必须至少为 32 字节");
        }
        if (accessTokenTtlSeconds <= 0) {
            throw new IllegalStateException("Access Token 有效期必须大于 0");
        }
        SecretKey key = new SecretKeySpec(secretBytes, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        this.decoder = jwtDecoder;
        this.issuer = issuer;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public IssuedJwt issue(Long userId, String sessionId) {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(accessTokenTtlSeconds);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim("sid", sessionId)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        String token = encoder.encode(
                JwtEncoderParameters.from(
                        org.springframework.security.oauth2.jwt.JwsHeader.with(MacAlgorithm.HS256).build(),
                        claims
                )
        ).getTokenValue();
        return new IssuedJwt(token, expiresAt);
    }

    public Jwt decode(String token) throws JwtException {
        return decoder.decode(token);
    }

    public record IssuedJwt(String token, Instant expiresAt) {
    }
}
