package com.autohr.modules.auth.service;

import com.autohr.common.exception.BusinessException;

public final class PasswordPolicy {

    private static final String STRONG_PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$";

    private PasswordPolicy() {
    }

    public static void requireStrongPassword(String password) {
        if (password == null || !password.matches(STRONG_PASSWORD_PATTERN)) {
            throw new BusinessException("密码必须至少8位且同时包含字母和数字");
        }
    }
}
