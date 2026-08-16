package com.autohr.modules.auth.dto;

import lombok.Data;

@Data
public class SessionUserVO {
    private Long id;
    private String username;
    private String displayName;
    private String roleCode;
    private String mobilePhone;
    private String email;
    private Integer status;
    private Integer profileCompleted;
    private Integer mustChangePassword;
}
