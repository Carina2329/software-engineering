package com.campus.contest.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * 生成不透明 Session ID 和 Refresh Token，并计算仅用于存储的摘要。
 */
@Component
public class SessionTokenCodec {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final char SEPARATOR = '.';

    public String newSessionId() {
        return randomToken(24);
    }

    public String newRefreshToken(String sessionId) {
        return sessionId + SEPARATOR + randomToken(32);
    }

    public Optional<String> extractSessionId(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        int separator = refreshToken.indexOf(SEPARATOR);
        if (separator <= 0 || separator != refreshToken.lastIndexOf(SEPARATOR)) {
            return Optional.empty();
        }
        String sessionId = refreshToken.substring(0, separator);
        String secret = refreshToken.substring(separator + 1);
        if (sessionId.isBlank() || secret.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(sessionId);
    }

    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return URL_ENCODER.encodeToString(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public boolean matches(String rawToken, String storedHash) {
        if (rawToken == null || storedHash == null) {
            return false;
        }
        byte[] actual = hash(rawToken).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = storedHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
    }

    private String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }
}
