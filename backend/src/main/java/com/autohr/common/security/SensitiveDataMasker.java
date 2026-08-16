package com.autohr.common.security;

public final class SensitiveDataMasker {

    private SensitiveDataMasker() {
    }

    public static String maskIdentityOrAccount(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String normalized = value.trim();
        if (normalized.length() <= 7) {
            return "*".repeat(normalized.length());
        }
        return normalized.substring(0, 3)
                + "*".repeat(normalized.length() - 7)
                + normalized.substring(normalized.length() - 4);
    }
}
