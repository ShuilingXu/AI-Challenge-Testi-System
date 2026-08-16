package com.autohr.modules.auth.dto;

import lombok.Data;

@Data
public class VerificationCodeRequest {
    private String mobilePhone;
    private String email;
    private String captchaId;
    private String captchaCode;
}
