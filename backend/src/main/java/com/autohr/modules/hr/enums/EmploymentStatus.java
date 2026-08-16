package com.autohr.modules.hr.enums;

public enum EmploymentStatus {
    PENDING_ONBOARDING(0),
    ACTIVE(1),
    INACTIVE(2),
    RESIGNED(3);

    private final int code;

    EmploymentStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
