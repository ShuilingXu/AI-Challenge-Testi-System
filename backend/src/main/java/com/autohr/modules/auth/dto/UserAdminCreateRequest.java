package com.autohr.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserAdminCreateRequest {
    @NotBlank @Size(max = 64)
    private String username;
    @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$", message = "密码必须至少8位且同时包含字母和数字")
    private String password;
    @NotBlank
    private String roleCode;
    @Size(max = 64)
    private String displayName;
    private String mobilePhone;
    private String email;
}
