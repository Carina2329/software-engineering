package com.campus.contest.module.system.service;

import com.campus.contest.module.system.enums.CaptchaScene;
import com.campus.contest.module.system.vo.CaptchaChallengeVO;

/**
 * 服务端图片人机验证码的生成与校验。
 */
public interface CaptchaService {

    CaptchaChallengeVO create(CaptchaScene scene, String clientIp);

    void verify(String captchaId, String captchaCode, CaptchaScene scene);
}
