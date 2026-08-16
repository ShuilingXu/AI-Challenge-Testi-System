package com.autohr.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CandidateRegisterRequest {
    @NotBlank(message = "用户名必填")
    private String username;
    @NotBlank(message = "密码必填")
    @Size(min = 8, message = "密码长度不能少于8位")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$", message = "密码必须同时包含字母和数字")
    private String password;
    @NotBlank(message = "姓名必填")
    private String displayName;
    private String mobilePhone;
    private String email;
    @NotBlank(message = "验证码必填")
    private String verificationCode;
    @NotBlank(message = "图形验证码ID必填")
    private String captchaId;
    @NotBlank(message = "图形验证码必填")
    private String captchaCode;
}
