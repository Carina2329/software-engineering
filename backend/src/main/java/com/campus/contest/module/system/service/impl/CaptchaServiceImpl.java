package com.campus.contest.module.system.service.impl;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.system.enums.CaptchaScene;
import com.campus.contest.module.system.service.CaptchaService;
import com.campus.contest.module.system.service.VerificationRateLimiter;
import com.campus.contest.module.system.service.VerificationTokens;
import com.campus.contest.module.system.vo.CaptchaChallengeVO;
import com.campus.contest.security.LoginCaptchaVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 图片验证码实现：答案仅存 Redis，2 分钟过期，最多错误 5 次，校验成功后立即删除。
 */
@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService, LoginCaptchaVerifier {

    private static final String KEY_PREFIX = "auth:captcha:";
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final int MAX_ATTEMPTS = 5;
    private static final int CODE_LENGTH = 4;
    private static final char[] CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final StringRedisTemplate redisTemplate;
    private final VerificationRateLimiter rateLimiter;

    @Override
    public CaptchaChallengeVO create(CaptchaScene scene, String clientIp) {
        if (scene == null) {
            throw invalidParam("scene 不能为空");
        }
        rateLimiter.checkCaptchaGeneration(clientIp);

        String code = randomCode();
        String captchaId = VerificationTokens.nextId();
        Instant expiresAt = Instant.now().plus(TTL);
        try {
            redisTemplate.opsForHash().putAll(KEY_PREFIX + captchaId, Map.of(
                    "scene", scene.name(),
                    "answerHash", VerificationTokens.sha256Hex(code),
                    "attempts", "0"
            ));
            redisTemplate.expire(KEY_PREFIX + captchaId, TTL);
        } catch (DataAccessException e) {
            throw unavailable();
        }
        return new CaptchaChallengeVO(captchaId, renderPngBase64(code), expiresAt);
    }

    @Override
    public void verify(String captchaId, String captchaCode, CaptchaScene scene) {
        if (captchaId == null || captchaId.isBlank()
                || captchaCode == null || captchaCode.isBlank()
                || scene == null) {
            throw invalidCaptcha();
        }
        String key = KEY_PREFIX + captchaId;
        try {
            Map<Object, Object> fields = redisTemplate.opsForHash().entries(key);
            if (fields == null || fields.isEmpty()) {
                throw invalidCaptcha();
            }
            String storedScene = str(fields.get("scene"));
            String answerHash = str(fields.get("answerHash"));
            int attempts = parseInt(str(fields.get("attempts")));
            if (!scene.name().equals(storedScene) || attempts >= MAX_ATTEMPTS) {
                redisTemplate.delete(key);
                throw invalidCaptcha();
            }
            String normalizedCode = captchaCode.trim().toUpperCase(Locale.ROOT);
            if (VerificationTokens.matches(normalizedCode, answerHash)) {
                redisTemplate.delete(key);
                return;
            }
            long next = redisTemplate.opsForHash().increment(key, "attempts", 1);
            if (next >= MAX_ATTEMPTS) {
                redisTemplate.delete(key);
            }
            throw invalidCaptcha();
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    @Override
    public void verify(String captchaId, String captchaCode) {
        verify(captchaId, captchaCode, CaptchaScene.LOGIN);
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CHARSET[ThreadLocalRandom.current().nextInt(CHARSET.length)]);
        }
        return code.toString();
    }

    private String renderPngBase64(String code) {
        int width = 120;
        int height = 40;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
            for (int i = 0; i < CODE_LENGTH; i++) {
                graphics.setColor(new Color(
                        ThreadLocalRandom.current().nextInt(30, 130),
                        ThreadLocalRandom.current().nextInt(30, 130),
                        ThreadLocalRandom.current().nextInt(30, 130)
                ));
                int x = 14 + i * 24 + ThreadLocalRandom.current().nextInt(-3, 4);
                int y = 28 + ThreadLocalRandom.current().nextInt(-4, 5);
                graphics.drawString(String.valueOf(code.charAt(i)), x, y);
            }
            for (int i = 0; i < 5; i++) {
                graphics.setColor(new Color(
                        ThreadLocalRandom.current().nextInt(120, 200),
                        ThreadLocalRandom.current().nextInt(120, 200),
                        ThreadLocalRandom.current().nextInt(120, 200)
                ));
                graphics.drawLine(
                        ThreadLocalRandom.current().nextInt(0, width),
                        ThreadLocalRandom.current().nextInt(0, height),
                        ThreadLocalRandom.current().nextInt(0, width),
                        ThreadLocalRandom.current().nextInt(0, height)
                );
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("验证码图片生成失败", e);
        } finally {
            graphics.dispose();
        }
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private BizException invalidCaptcha() {
        return new BizException(
                ErrorCode.INVALID_PARAM.getCode(),
                "图片验证码不正确或已失效"
        );
    }

    private BizException invalidParam(String message) {
        return new BizException(ErrorCode.INVALID_PARAM.getCode(), message);
    }

    private BizException unavailable() {
        return new BizException(
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getMessage()
        );
    }
}
