package com.autohr.modules.auth.service;

public interface VerificationCodeService {
    void sendRegisterCode(String mobilePhone, String email, String captchaId, String captchaCode);
    void verifyRegisterCode(String mobilePhone, String email, String code);
    void sendPasswordResetCode(String mobilePhone, String email, String captchaId, String captchaCode, boolean deliver);
    void verifyPasswordResetCode(String mobilePhone, String email, String code);
}
