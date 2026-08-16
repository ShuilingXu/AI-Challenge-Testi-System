package com.autohr.modules.hr.controller;

import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.service.AuditLogService;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.hr.dto.ImportResultVO;
import com.autohr.modules.hr.service.EmployeeExcelService;
import com.autohr.modules.hr.service.PayrollService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayrollImportAuditTest {
    @Test
    void payrollImportAuditsEverySuccessfulEmployeeMonthAndTheBatch() {
        PayrollService service = mock(PayrollService.class);
        AuditLogService audit = mock(AuditLogService.class);
        AuthService auth = mock(AuthService.class);
        Authentication authentication = authentication(auth);
        ImportResultVO result = new ImportResultVO();
        result.success(2, "Imported", 42L, "2026-08");
        result.failure(3, "Invalid amount");
        when(service.importPerformance(any(), any())).thenReturn(result);

        new PayrollController(service, auth, audit).importPerformance(authentication,
                new MockMultipartFile("file", "performance.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1}));

        verify(audit).log(9L, "管理员", "HR_ADMIN", "PAYROLL", "PERFORMANCE_IMPORT_ROW", "HR_PAYROLL", "42", "2026-08");
        verify(audit).log(9L, "管理员", "HR_ADMIN", "PAYROLL", "PERFORMANCE_IMPORT", "HR_PAYROLL", "BATCH", "success=1, failure=1");
    }

    @Test
    void employeeImportAuditsEveryCreatedSalaryAndTheBatch() {
        EmployeeExcelService service = mock(EmployeeExcelService.class);
        AuditLogService audit = mock(AuditLogService.class);
        AuthService auth = mock(AuthService.class);
        Authentication authentication = authentication(auth);
        ImportResultVO result = new ImportResultVO();
        result.success(2, "导入成功", 88L, "2026-08");
        when(service.importEmployees(any(), any())).thenReturn(result);

        new EmployeeExcelController(service, auth, audit).importFile(authentication,
                new MockMultipartFile("file", "employees.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1}));

        verify(audit).log(9L, "管理员", "HR_ADMIN", "PAYROLL", "CREATE_EMPLOYEE_SALARY", "HR_EMPLOYEE", "88", "row=2, month=2026-08");
        verify(audit).log(9L, "管理员", "HR_ADMIN", "PAYROLL", "EMPLOYEE_IMPORT", "HR_EMPLOYEE", "BATCH", "success=1, failure=0");
    }

    private Authentication authentication(AuthService authService) {
        Authentication authentication = mock(Authentication.class);
        SessionUserVO user = new SessionUserVO();
        user.setId(9L);
        user.setDisplayName("管理员");
        user.setRoleCode("HR_ADMIN");
        when(authentication.getName()).thenReturn("admin");
        when(authService.loadUserByUsername("admin")).thenReturn(user);
        return authentication;
    }
}
