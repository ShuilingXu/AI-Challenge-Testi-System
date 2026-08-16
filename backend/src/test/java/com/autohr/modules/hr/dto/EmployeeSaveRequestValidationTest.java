package com.autohr.modules.hr.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeSaveRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void newEmployeeRequiresPositiveSalaryWhileAnEditMayRecordZero() {
        EmployeeSaveRequest request = validRequest();
        request.setBaseSalary(BigDecimal.ZERO);
        assertFalse(validator.validate(request).isEmpty());

        request.setId(10L);
        assertTrue(validator.validate(request).isEmpty());
    }

    private EmployeeSaveRequest validRequest() {
        EmployeeSaveRequest request = new EmployeeSaveRequest();
        request.setFullName("测试员工");
        request.setIdCardNo("110101199001011234");
        request.setMobilePhone("13800138000");
        request.setRecruitmentMajor("计算机");
        request.setJobId(1L);
        request.setDepartmentId(1L);
        request.setBaseSalary(new BigDecimal("10000.00"));
        request.setBankAccountNo("6222020000000000");
        request.setBankName("测试银行");
        return request;
    }
}
