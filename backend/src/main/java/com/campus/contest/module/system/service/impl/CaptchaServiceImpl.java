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
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

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
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 原子写入验证码答案和 TTL。
     */
    private static final RedisScript<Long> CREATE_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('HSET', KEYS[1],
              'scene', ARGV[1],
              'answerHash', ARGV[2],
              'attempts', '0')
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return 1
            """,
            Long.class
    );

    /**
     * 原子校验：1=成功，0=不存在，2=场景不匹配，3=已超次数，4=错误次数达上限，5=错误未达上限。
     */
    private static final RedisScript<Long> VERIFY_SCRIPT = new DefaultRedisScript<>(
            """
            local stored = redis.call('HGETALL', KEYS[1])
            if #stored == 0 then
              return 0
            end
            local fields = {}
            for i = 1, #stored, 2 do
              fields[stored[i]] = stored[i + 1]
            end
            if fields['scene'] ~= ARGV[1] then
              redis.call('DEL', KEYS[1])
              return 2
            end
            local attempts = tonumber(fields['attempts'] or '0')
            if attempts >= tonumber(ARGV[3]) then
              redis.call('DEL', KEYS[1])
              return 3
            end
            if fields['answerHash'] == ARGV[2] then
              redis.call('DEL', KEYS[1])
              return 1
            end
            attempts = attempts + 1
            if attempts >= tonumber(ARGV[3]) then
              redis.call('DEL', KEYS[1])
              return 4
            end
            redis.call('HSET', KEYS[1], 'attempts', tostring(attempts))
            return 5
            """,
            Long.class
    );

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
            Long result = redisTemplate.execute(
                    CREATE_SCRIPT,
                    List.of(KEY_PREFIX + captchaId),
                    scene.name(),
                    VerificationTokens.sha256Hex(code),
                    String.valueOf(TTL.toMillis())
            );
            if (result == null || result != 1L) {
                throw unavailable();
            }
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
        String normalizedCode = captchaCode.trim().toUpperCase(Locale.ROOT);
        try {
            Long result = redisTemplate.execute(
                    VERIFY_SCRIPT,
                    List.of(KEY_PREFIX + captchaId),
                    scene.name(),
                    VerificationTokens.sha256Hex(normalizedCode),
                    String.valueOf(MAX_ATTEMPTS)
            );
            if (result == null || result != 1L) {
                throw invalidCaptcha();
            }
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
            code.append(CHARSET[SECURE_RANDOM.nextInt(CHARSET.length)]);
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
                        SECURE_RANDOM.nextInt(161) + 30,
                        SECURE_RANDOM.nextInt(161) + 30,
                        SECURE_RANDOM.nextInt(161) + 30
                ));
                int x = 14 + i * 24 + SECURE_RANDOM.nextInt(7) - 3;
                int y = 28 + SECURE_RANDOM.nextInt(9) - 4;
                graphics.drawString(String.valueOf(code.charAt(i)), x, y);
            }
            for (int i = 0; i < 5; i++) {
                graphics.setColor(new Color(
                        SECURE_RANDOM.nextInt(81) + 120,
                        SECURE_RANDOM.nextInt(81) + 120,
                        SECURE_RANDOM.nextInt(81) + 120
                ));
                graphics.drawLine(
                        SECURE_RANDOM.nextInt(width),
                        SECURE_RANDOM.nextInt(height),
                        SECURE_RANDOM.nextInt(width),
                        SECURE_RANDOM.nextInt(height)
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
