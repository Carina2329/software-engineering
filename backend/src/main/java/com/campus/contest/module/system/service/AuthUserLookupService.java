package com.campus.contest.module.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * 认证专用只读用户查询，不创建第二套用户 Mapper。
 */
@Service
@RequiredArgsConstructor
public class AuthUserLookupService {

    private final SysUserMapper userMapper;

    public Optional<SysUserDO> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(userMapper.selectOne(
                Wrappers.<SysUserDO>lambdaQuery()
                        .eq(SysUserDO::getUsername, username.trim())
                        .last("LIMIT 1")
        ));
    }

    public Optional<SysUserDO> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(userMapper.selectOne(
                Wrappers.<SysUserDO>lambdaQuery()
                        .eq(SysUserDO::getEmail, email.trim().toLowerCase(Locale.ROOT))
                        .last("LIMIT 1")
        ));
    }
}
