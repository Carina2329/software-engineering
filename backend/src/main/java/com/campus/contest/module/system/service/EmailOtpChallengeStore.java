package com.campus.contest.module.system.service;

import com.campus.contest.module.system.enums.EmailOtpScene;

import java.time.Instant;

/**
 * 邮件 OTP challenge 的跨模块查询和原子回滚边界。
 */
public interface EmailOtpChallengeStore {

    /**
     * 判断 challenge 是否存在、未过期，并且仍是该邮箱和场景对应的当前版本。
     */
    boolean isCurrentAndActive(
            String challengeId,
            String email,
            EmailOtpScene scene,
            Instant now
    );

    /**
     * 邮件任务发布失败后的原子回滚。
     *
     * <p>只删除本请求的 challenge。仅当当前 challenge 索引仍指向该 challenge
     * 时才删除索引，绝不清理后续请求创建的新 challenge。</p>
     */
    void rollbackFailedDispatch(
            String challengeId,
            String email,
            EmailOtpScene scene
    );
}
