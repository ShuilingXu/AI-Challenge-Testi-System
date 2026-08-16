package com.autohr.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "用户名必填")
    private String username;
    @NotBlank(message = "密码必填")
    private String password;
    @NotBlank(message = "图形验证码ID必填")
    private String captchaId;
    @NotBlank(message = "图形验证码必填")
    private String captchaCode;
}
