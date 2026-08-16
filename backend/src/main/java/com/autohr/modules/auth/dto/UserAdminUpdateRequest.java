package com.autohr.modules.auth.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UserAdminUpdateRequest {
    private String roleCode;
    private Integer status;
    private String displayName;
    private String mobilePhone;
    private String email;
    @Pattern(regexp = "^$|^(?=.*[A-Za-z])(?=.*\\d).{8,}$", message = "新密码必须至少8位且同时包含字母和数字")
    private String newPassword;
}
