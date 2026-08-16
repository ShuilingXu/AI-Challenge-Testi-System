package com.autohr.modules.hr.service.impl;

import com.autohr.common.exception.BusinessException;
import com.autohr.config.database.ActiveDatabase;
import com.autohr.config.database.DatabaseType;
import com.autohr.modules.hr.dto.MonthlyPerformanceRequest;
import com.autohr.modules.hr.dto.PayrollVO;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PayrollServiceImplTest {
    @Test
    void dialectUpsertsProtectLockedPayrollRows() {
        String mysql = PayrollServiceImpl.payrollUpsertSql(DatabaseType.MYSQL);
        assertTrue(mysql.contains("base_salary=IF(locked=0,VALUES(base_salary),base_salary)"));
        assertTrue(mysql.contains("calculated_at=IF(locked=0,CURRENT_TIMESTAMP,calculated_at)"));

        String postgres = PayrollServiceImpl.payrollUpsertSql(DatabaseType.PGSQL);
        assertTrue(postgres.contains("ON CONFLICT (employee_id,salary_month) DO UPDATE"));
        assertTrue(postgres.endsWith("WHERE hr_payroll_month.locked=0"));
    }

    @Test
    void mysqlUpsertDetectsALockAcquiredDuringGeneration() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PayrollServiceImpl service = new PayrollServiceImpl(jdbc, mock(Validator.class));
        service.configureDatabase(new ActiveDatabase(DatabaseType.MYSQL, "", "", "", false));
        PayrollVO payroll = new PayrollVO();
        payroll.setEmployeeId(7L);
        payroll.setSalaryMonth("2026-08");
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(
                eq("SELECT COUNT(*) FROM hr_payroll_month WHERE employee_id=? AND salary_month=? AND locked=1"),
                eq(Long.class), eq(7L), eq("2026-08"))).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.upsertPayroll(payroll));
    }

    @Test
    void lockedPayrollRejectsDirectMonthlyWrite() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Validator validator = mock(Validator.class);
        PayrollServiceImpl service = new PayrollServiceImpl(jdbc, validator);
        MonthlyPerformanceRequest request = new MonthlyPerformanceRequest();
        request.setEmployeeId(7L);
        request.setSalaryMonth("2026-08");
        request.setAmount(new BigDecimal("1000.00"));
        when(jdbc.queryForObject(
                eq("SELECT COUNT(*) FROM hr_payroll_month WHERE employee_id=? AND salary_month=? AND locked=1"),
                eq(Long.class), eq(7L), eq("2026-08"))).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.savePerformance(request, 99L));
        verifyNoMoreInteractions(validator);
    }
}
