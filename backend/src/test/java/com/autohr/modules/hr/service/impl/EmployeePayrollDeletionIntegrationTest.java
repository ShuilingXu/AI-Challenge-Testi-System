package com.autohr.modules.hr.service.impl;

import com.autohr.common.exception.BusinessException;
import com.autohr.config.database.ActiveDatabase;
import com.autohr.config.database.AppMigrationProperties;
import com.autohr.config.database.DatabaseMigrationRunner;
import com.autohr.config.database.DatabaseType;
import com.autohr.modules.hr.entity.Employee;
import com.autohr.modules.hr.mapper.DepartmentMapper;
import com.autohr.modules.hr.mapper.EmployeeMapper;
import com.autohr.modules.hr.mapper.IntegrationBindingMapper;
import com.autohr.modules.hr.mapper.SalaryHistoryMapper;
import com.autohr.modules.hr.service.HrStatisticsService;
import com.autohr.modules.recruitment.mapper.RecruitmentJobMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.Serializable;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeePayrollDeletionIntegrationTest {
    @TempDir
    Path tempDirectory;

    @Test
    void everyPayrollRelatedTableBlocksEmployeeDeletionWithABusinessError() throws Exception {
        JdbcTemplate jdbc = migratedDatabase();
        jdbc.update("INSERT INTO hr_department (department_code,department_name,description) VALUES ('D1','Payroll','Payroll')");
        jdbc.update("INSERT INTO recruitment_job (job_code,job_title,department_name,requirements,responsibilities,publish_date) VALUES ('J1','Engineer','Payroll','Requirements','Responsibilities','2026-01-01')");
        for (int sequence = 1; sequence <= 6; sequence++) {
            insertEmployee(jdbc, sequence);
        }
        jdbc.update("INSERT INTO hr_payroll_month (employee_id,salary_month,base_salary,performance,overtime_hours,overtime_pay,gross_income,social_insurance_total,special_deduction_total,taxable_income_month,cumulative_income,cumulative_deduction_base,cumulative_social_insurance,cumulative_special_deduction,cumulative_taxable_income,cumulative_tax_withheld,current_tax_withheld,net_pay,locked) VALUES (1,'2026-08',10000,0,0,0,10000,0,0,5000,10000,5000,0,0,5000,150,150,9850,0)");
        jdbc.update("INSERT INTO hr_performance_month (employee_id,salary_month,amount) VALUES (2,'2026-08',100)");
        jdbc.update("INSERT INTO hr_overtime_month (employee_id,salary_month) VALUES (3,'2026-08')");
        jdbc.update("INSERT INTO hr_social_insurance_month (employee_id,salary_month) VALUES (4,'2026-08')");
        jdbc.update("INSERT INTO hr_special_deduction_month (employee_id,salary_month) VALUES (5,'2026-08')");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (6,'2026-08',0,10000)");

        EmployeeMapper employeeMapper = mock(EmployeeMapper.class);
        when(employeeMapper.selectById(any(Serializable.class))).thenAnswer(invocation -> {
            Employee employee = new Employee();
            employee.setId(((Number) invocation.getArgument(0)).longValue());
            return employee;
        });
        HrServiceImpl hrService = new HrServiceImpl(
                mock(DepartmentMapper.class), employeeMapper, mock(IntegrationBindingMapper.class),
                mock(RecruitmentJobMapper.class), mock(SalaryHistoryMapper.class), jdbc,
                mock(HrStatisticsService.class));

        for (long employeeId = 1; employeeId <= 6; employeeId++) {
            long currentEmployeeId = employeeId;
            BusinessException error = assertThrows(BusinessException.class,
                    () -> hrService.deleteEmployee(currentEmployeeId));
            assertEquals("该员工已有薪资记录，不能删除", error.getMessage());
        }

        verify(employeeMapper, never()).deleteById(any(Serializable.class));
        assertEquals(6L, jdbc.queryForObject("SELECT COUNT(*) FROM hr_employee", Long.class));
    }

    private JdbcTemplate migratedDatabase() throws Exception {
        String url = "jdbc:sqlite:" + tempDirectory.resolve("employee-delete.db").toString().replace('\\', '/');
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        new DatabaseMigrationRunner(dataSource, new ActiveDatabase(DatabaseType.SQLITE, url, "", "", false),
                new AppMigrationProperties()).run();
        return new JdbcTemplate(dataSource);
    }

    private void insertEmployee(JdbcTemplate jdbc, int sequence) {
        jdbc.update("INSERT INTO hr_employee (employee_code,full_name,id_card_no,mobile_phone,recruitment_major,position_name,department_id,bank_account_no,bank_name,hire_date,employment_status,job_id,base_salary,salary_confirmed) VALUES (?,?,?,?,?,?,?,?,?,'2026-01-01',1,1,10000,1)",
                "E00" + sequence, "Employee " + sequence, "1101011990010100" + String.format("%02d", sequence),
                "138000000" + String.format("%02d", sequence), "Engineering", "Engineer", 1,
                "62220000000000" + String.format("%02d", sequence), "Test Bank");
    }
}
