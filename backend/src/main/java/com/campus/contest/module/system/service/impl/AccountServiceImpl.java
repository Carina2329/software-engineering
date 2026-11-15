package com.campus.contest.module.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.system.dto.AuditLogCommand;
import com.campus.contest.module.system.dto.PasswordResetConfirm;
import com.campus.contest.module.system.dto.RegisterRequest;
import com.campus.contest.module.system.entity.SysRoleDO;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.entity.SysUserRoleDO;
import com.campus.contest.module.system.enums.EmailOtpScene;
import com.campus.contest.module.system.mapper.SysRoleMapper;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.system.mapper.SysUserRoleMapper;
import com.campus.contest.module.system.service.AccountService;
import com.campus.contest.module.system.service.AuditLogService;
import com.campus.contest.module.system.service.AuthUserLookupService;
import com.campus.contest.module.system.service.EmailOtpService;
import com.campus.contest.module.system.service.VerificationRateLimiter;
import com.campus.contest.module.system.vo.RegisterResultVO;
import com.campus.contest.security.SessionRevocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 注册与找回密码实现：MySQL 事务只覆盖表操作，提交后再撤销 Redis 会话。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceImpl implements AccountService {

    private static final int USER_ACTIVE = 1;
    private static final long SYSTEM_OPERATOR = 0L;
    private static final String STUDENT_ROLE_CODE = "STUDENT";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{4,32}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern EMAIL_CODE_PATTERN = Pattern.compile("^[0-9]{6}$");
    private static final int REVOKE_MAX_ATTEMPTS = 3;

    private final EmailOtpService emailOtpService;
    private final AuthUserLookupService userLookupService;
    private final PasswordEncoder passwordEncoder;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SessionRevocationService sessionRevocationService;
    private final VerificationRateLimiter rateLimiter;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public RegisterResultVO register(RegisterRequest request, String clientIp) {
        validateRegister(request);
        String normalizedEmail = normalizeEmail(request.getEmail());
        rateLimiter.checkRegister(clientIp);
        emailOtpService.verify(
                request.getChallengeId(),
                normalizedEmail,
                request.getEmailCode(),
                EmailOtpScene.REGISTER
        );

        String username = request.getUsername().trim();
        String realName = request.getRealName().trim();
        ensureUsernameUnique(username);
        ensureEmailUnique(normalizedEmail);
        String passwordHash = passwordEncoder.encode(request.getPassword());

        Long userId = transactionTemplate.execute(status -> {
            SysUserDO user = new SysUserDO();
            user.setUsername(username);
            user.setRealName(realName);
            user.setPasswordHash(passwordHash);
            user.setEmail(normalizedEmail);
            user.setStatus(USER_ACTIVE);
            LocalDateTime now = LocalDateTime.now();
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            user.setCreatedBy(SYSTEM_OPERATOR);
            user.setUpdatedBy(SYSTEM_OPERATOR);
            user.setDeleted(0);
            user.setVersion(0);
            try {
                userMapper.insert(user);
            } catch (DuplicateKeyException e) {
                throw duplicateAccount();
            }

            SysRoleDO studentRole = roleMapper.selectOne(
                    Wrappers.<SysRoleDO>lambdaQuery()
                            .eq(SysRoleDO::getRoleCode, STUDENT_ROLE_CODE)
                            .last("LIMIT 1")
            );
            if (studentRole == null) {
                throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "默认学生角色缺失");
            }
            SysUserRoleDO userRole = new SysUserRoleDO();
            userRole.setUserId(user.getId());
            userRole.setRoleId(studentRole.getId());
            try {
                userRoleMapper.insert(userRole);
            } catch (DuplicateKeyException e) {
                throw duplicateAccount();
            }
            return user.getId();
        });

        if (userId == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "注册失败");
        }
        return new RegisterResultVO(String.valueOf(userId), username);
    }

    @Override
    public Long confirmPasswordReset(PasswordResetConfirm request, String clientIp) {
        validateResetConfirm(request);
        String normalizedEmail = normalizeEmail(request.getEmail());
        emailOtpService.verify(
                request.getChallengeId(),
                normalizedEmail,
                request.getEmailCode(),
                EmailOtpScene.RESET_PASSWORD
        );

        SysUserDO user = userLookupService.findByEmail(normalizedEmail)
                .filter(item -> Objects.equals(USER_ACTIVE, item.getStatus()))
                .orElseThrow(this::invalidResetRequest);

        String newPasswordHash = passwordEncoder.encode(request.getNewPassword());
        Long userId = user.getId();
        Integer updated = transactionTemplate.execute(status -> {
            SysUserDO toUpdate = new SysUserDO();
            toUpdate.setId(userId);
            toUpdate.setPasswordHash(newPasswordHash);
            toUpdate.setUpdatedAt(LocalDateTime.now());
            toUpdate.setUpdatedBy(userId);
            toUpdate.setVersion(user.getVersion());
            return userMapper.updateById(toUpdate);
        });

        if (updated == null || updated != 1) {
            throw new BizException(
                    ErrorCode.BUSINESS_CONFLICT.getCode(),
                    ErrorCode.BUSINESS_CONFLICT.getMessage()
            );
        }

        revokeAllSessionsWithRetry(userId);
        return userId;
    }

    private void validateRegister(RegisterRequest request) {
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        if (request.getUsername() == null
                || !USERNAME_PATTERN.matcher(request.getUsername().trim()).matches()) {
            throw invalidParam("username 必须为 4-32 位字母、数字或下划线");
        }
        if (request.getRealName() == null
                || request.getRealName().trim().isBlank()
                || request.getRealName().trim().length() > 32) {
            throw invalidParam("real_name 长度必须为 1-32");
        }
        if (request.getPassword() == null
                || request.getPassword().length() < 8
                || request.getPassword().length() > 64) {
            throw invalidParam("password 长度必须为 8-64");
        }
        if (request.getChallengeId() == null || request.getChallengeId().isBlank()) {
            throw invalidParam("challenge_id 不能为空");
        }
        if (request.getEmailCode() == null
                || !EMAIL_CODE_PATTERN.matcher(request.getEmailCode()).matches()) {
            throw invalidParam("email_code 必须是 6 位数字");
        }
    }

    private void validateResetConfirm(PasswordResetConfirm request) {
        if (request == null) {
            throw invalidParam("请求体不能为空");
        }
        if (request.getChallengeId() == null || request.getChallengeId().isBlank()) {
            throw invalidParam("challenge_id 不能为空");
        }
        if (request.getEmailCode() == null
                || !EMAIL_CODE_PATTERN.matcher(request.getEmailCode()).matches()) {
            throw invalidParam("email_code 必须是 6 位数字");
        }
        if (request.getNewPassword() == null
                || request.getNewPassword().length() < 8
                || request.getNewPassword().length() > 64) {
            throw invalidParam("new_password 长度必须为 8-64");
        }
    }

    private void ensureUsernameUnique(String username) {
        if (userLookupService.findByUsername(username).isPresent()) {
            throw duplicateAccount();
        }
    }

    private void ensureEmailUnique(String email) {
        if (userLookupService.findByEmail(email).isPresent()) {
            throw duplicateAccount();
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.length() > 128
                || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw invalidParam("email 格式不合法");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void revokeAllSessionsWithRetry(Long userId) {
        BizException lastFailure = null;
        for (int attempt = 1; attempt <= REVOKE_MAX_ATTEMPTS; attempt++) {
            try {
                sessionRevocationService.revokeAllSessions(userId);
                return;
            } catch (BizException e) {
                lastFailure = e;
                log.warn("重置密码后会话撤销失败，第 {} 次尝试，code={}", attempt, e.getCode());
            }
        }
        recordSessionRevocationFailure(userId, lastFailure);
        throw lastFailure;
    }

    private void recordSessionRevocationFailure(Long userId, BizException failure) {
        try {
            auditLogService.record(new AuditLogCommand(
                    userId,
                    "AUTH",
                    "SESSION_REVOCATION_FAILED",
                    "/api/v1/auth/password-reset/confirm",
                    "POST",
                    "{\"reason\":\"password_reset\"}",
                    null,
                    0,
                    failure == null
                            ? ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode()
                            : failure.getCode(),
                    null
            ));
        } catch (RuntimeException e) {
            log.warn("会话撤销失败审计写入失败: {}", e.getClass().getSimpleName());
        }
    }

    private BizException duplicateAccount() {
        return new BizException(
                ErrorCode.DUPLICATE_RELATION.getCode(),
                "用户名或邮箱已存在"
        );
    }

    private BizException invalidResetRequest() {
        return new BizException(
                ErrorCode.INVALID_PARAM.getCode(),
                "找回密码请求无效或已过期"
        );
    }

    private BizException invalidParam(String message) {
        return new BizException(ErrorCode.INVALID_PARAM.getCode(), message);
    }
}
