package com.autohr.modules.auth.service;

import com.autohr.modules.auth.dto.CaptchaVO;

public interface CaptchaService {
    CaptchaVO createCaptcha();
    void verifyCaptcha(String captchaId, String captchaCode);
}
