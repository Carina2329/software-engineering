package com.campus.contest.module.system.service.impl;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.system.dto.PasswordResetConfirm;
import com.campus.contest.module.system.dto.RegisterRequest;
import com.campus.contest.module.system.entity.SysRoleDO;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.mapper.SysRoleMapper;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.system.mapper.SysUserRoleMapper;
import com.campus.contest.module.system.service.AuditLogService;
import com.campus.contest.module.system.service.AuthUserLookupService;
import com.campus.contest.module.system.service.EmailOtpService;
import com.campus.contest.module.system.service.VerificationRateLimiter;
import com.campus.contest.module.system.vo.RegisterResultVO;
import com.campus.contest.security.SessionRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceImplTest {

    private EmailOtpService emailOtpService;
    private AuthUserLookupService userLookupService;
    private SysUserMapper userMapper;
    private SysRoleMapper roleMapper;
    private SessionRevocationService sessionRevocationService;
    private TransactionTemplate transactionTemplate;
    private AccountServiceImpl accountService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        emailOtpService = mock(EmailOtpService.class);
        userLookupService = mock(AuthUserLookupService.class);
        userMapper = mock(SysUserMapper.class);
        roleMapper = mock(SysRoleMapper.class);
        sessionRevocationService = mock(SessionRevocationService.class);
        transactionTemplate = mock(TransactionTemplate.class);
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
        accountService = new AccountServiceImpl(
                emailOtpService,
                userLookupService,
                passwordEncoder,
                userMapper,
                roleMapper,
                mock(SysUserRoleMapper.class),
                sessionRevocationService,
                mock(VerificationRateLimiter.class),
                mock(AuditLogService.class),
                transactionTemplate
        );

        when(transactionTemplate.execute(any())).thenAnswer((Answer<Object>) invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    void registerAssignsStudentRoleAndReturnsId() {
        when(userLookupService.findByUsername("new_user")).thenReturn(Optional.empty());
        when(userLookupService.findByEmail("new_user@example.edu")).thenReturn(Optional.empty());
        SysRoleDO studentRole = new SysRoleDO();
        studentRole.setId(10L);
        studentRole.setRoleCode("STUDENT");
        when(roleMapper.selectOne(any())).thenReturn(studentRole);
        when(userMapper.insert(any(SysUserDO.class))).thenAnswer(invocation -> {
            SysUserDO user = invocation.getArgument(0);
            user.setId(99L);
            return 1;
        });

        RegisterResultVO result = accountService.register(registerRequest(), "127.0.0.1");

        assertEquals("99", result.id());
        assertEquals("new_user", result.username());
    }

    @Test
    void registerRejectsDuplicateUsername() {
        SysUserDO existing = new SysUserDO();
        existing.setId(1L);
        existing.setUsername("new_user");
        when(userLookupService.findByUsername("new_user")).thenReturn(Optional.of(existing));

        BizException exception = assertThrows(
                BizException.class,
                () -> accountService.register(registerRequest(), "127.0.0.1")
        );

        assertEquals(ErrorCode.DUPLICATE_RELATION.getCode(), exception.getCode());
    }

    @Test
    void resetPasswordRevokesAllSessions() {
        when(userLookupService.findByEmail("new_user@example.edu"))
                .thenReturn(Optional.of(activeUser()));
        when(userMapper.updateById(any(SysUserDO.class))).thenReturn(1);

        Long userId = accountService.confirmPasswordReset(resetConfirm(), "127.0.0.1");

        assertEquals(99L, userId);
        verify(sessionRevocationService).revokeAllSessions(99L);
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("new_user");
        request.setRealName("新用户");
        request.setPassword("Password#2026");
        request.setEmail("new_user@example.edu");
        request.setChallengeId("challenge-1");
        request.setEmailCode("123456");
        return request;
    }

    private PasswordResetConfirm resetConfirm() {
        PasswordResetConfirm request = new PasswordResetConfirm();
        request.setEmail("new_user@example.edu");
        request.setChallengeId("challenge-1");
        request.setEmailCode("123456");
        request.setNewPassword("NewPassword#2026");
        return request;
    }

    private SysUserDO activeUser() {
        SysUserDO user = new SysUserDO();
        user.setId(99L);
        user.setUsername("new_user");
        user.setEmail("new_user@example.edu");
        user.setStatus(1);
        user.setVersion(0);
        return user;
    }
}
