package com.campus.contest.security;

/**
 * 认证核心对图片验证码校验能力的窄端口。
 *
 * <p>该端口只服务于统一登录入口，不保存验证码、不生成验证码。
 * 图片验证码的具体实现由同学 2 提供并在集成时适配。</p>
 */
public interface LoginCaptchaVerifier {

    /**
     * 校验 LOGIN 场景的图片验证码。校验失败抛出统一业务异常。
     */
    void verify(String captchaId, String captchaCode);
}
