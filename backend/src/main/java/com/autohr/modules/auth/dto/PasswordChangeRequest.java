package com.autohr.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordChangeRequest {
    @NotBlank(message = "当前密码必填")
    private String currentPassword;

    @NotBlank(message = "新密码必填")
    @Size(min = 8, message = "新密码长度不能少于8位")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$", message = "新密码必须同时包含字母和数字")
    private String newPassword;
}
