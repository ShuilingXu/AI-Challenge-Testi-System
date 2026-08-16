package com.autohr.modules.auth.service;

import cn.hutool.core.util.StrUtil;
import com.autohr.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class AuthRateLimitService {

    private final AuthRedisSecurityStore securityStore;
    private final boolean trustForwardedHeaders;
    private final int windowSeconds;
    private final int loginPerIp;
    private final int loginPerUsername;
    private final int captchaPerIp;
    private final int verificationPerIp;
    private final int verificationPerTarget;

    public AuthRateLimitService(AuthRedisSecurityStore securityStore,
                                @Value("${auth.security.trust-forwarded-headers:false}") boolean trustForwardedHeaders,
                                @Value("${auth.security.rate-limit.window-seconds:60}") int windowSeconds,
                                @Value("${auth.security.rate-limit.login-per-ip:10}") int loginPerIp,
                                @Value("${auth.security.rate-limit.login-per-username:10}") int loginPerUsername,
                                @Value("${auth.security.rate-limit.captcha-per-ip:30}") int captchaPerIp,
                                @Value("${auth.security.rate-limit.verification-per-ip:5}") int verificationPerIp,
                                @Value("${auth.security.rate-limit.verification-per-target:3}") int verificationPerTarget) {
        this.securityStore = securityStore;
        this.trustForwardedHeaders = trustForwardedHeaders;
        this.windowSeconds = windowSeconds;
        this.loginPerIp = loginPerIp;
        this.loginPerUsername = loginPerUsername;
        this.captchaPerIp = captchaPerIp;
        this.verificationPerIp = verificationPerIp;
        this.verificationPerTarget = verificationPerTarget;
    }

    public void checkCaptchaIssue(HttpServletRequest request) {
        securityStore.enforceRateLimit("captcha-ip", clientAddress(request), captchaPerIp, windowSeconds,
                "图形验证码请求过于频繁，请稍后重试");
    }

    public void checkLogin(HttpServletRequest request, String username) {
        securityStore.enforceRateLimit("login-ip", clientAddress(request), loginPerIp, windowSeconds,
                "登录请求过于频繁，请稍后重试");
        if (StrUtil.isNotBlank(username)) {
            securityStore.enforceRateLimit("login-username", username.trim().toLowerCase(Locale.ROOT), loginPerUsername, windowSeconds,
                    "该账号登录尝试过于频繁，请稍后重试");
        }
    }

    public void checkVerificationSend(HttpServletRequest request, String purpose, String mobilePhone, String email) {
        securityStore.enforceRateLimit("verification-ip:" + purpose, clientAddress(request), verificationPerIp, windowSeconds,
                "验证码请求过于频繁，请稍后重试");
        securityStore.enforceRateLimit("verification-target:" + purpose, normalizeTarget(mobilePhone, email), verificationPerTarget, windowSeconds,
                "该联系方式验证码请求过于频繁，请稍后重试");
    }

    private String clientAddress(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (StrUtil.isNotBlank(forwardedFor)) {
                return forwardedFor.split(",", 2)[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private String normalizeTarget(String mobilePhone, String email) {
        boolean hasPhone = StrUtil.isNotBlank(mobilePhone);
        boolean hasEmail = StrUtil.isNotBlank(email);
        if (hasPhone == hasEmail) {
            throw new BusinessException("手机号和邮箱必须择一提供");
        }
        return hasPhone ? "sms:" + mobilePhone.trim() : "email:" + email.trim().toLowerCase(Locale.ROOT);
    }
}
