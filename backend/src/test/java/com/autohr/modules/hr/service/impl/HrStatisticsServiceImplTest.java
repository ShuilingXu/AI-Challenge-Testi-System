package com.autohr.modules.hr.service.impl;

import com.autohr.config.database.ActiveDatabase;
import com.autohr.config.database.AppMigrationProperties;
import com.autohr.config.database.DatabaseMigrationRunner;
import com.autohr.config.database.DatabaseType;
import com.autohr.modules.hr.dto.HrStatisticsVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HrStatisticsServiceImplTest {
    @TempDir Path tempDirectory;

    @Test
    void usesLiveMonthlyInputsAndExcludesUnconfirmedEmployees() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("statistics.db");
        jdbc.update("INSERT INTO hr_department (department_code,department_name,description) VALUES ('D1','研发部','研发')");
        jdbc.update("INSERT INTO recruitment_job (job_code,job_title,department_name,requirements,responsibilities,publish_date) VALUES ('J1','工程师','研发部','要求','职责','2026-01-01')");
        insertEmployee(jdbc, "E001", "已确认员工", "110101199001010011", "13800000001", 1, new BigDecimal("10000.00"));
        insertEmployee(jdbc, "E002", "未确认员工", "110101199001010022", "13800000002", 0, new BigDecimal("99999.00"));
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-01',0,10000)");
        jdbc.update("INSERT INTO hr_performance_month (employee_id,salary_month,amount) VALUES (1,'2026-08',1000)");
        jdbc.update("INSERT INTO hr_overtime_month (employee_id,salary_month,overtime_hours,unit_rate,overtime_pay) VALUES (1,'2026-08',10,40,400)");

        HrStatisticsVO statistics = new HrStatisticsServiceImpl(jdbc).statistics("2026-08");

        assertEquals(new BigDecimal("11400.00"), statistics.getSalary().getGrossTotal());
        assertEquals(new BigDecimal("11400.00"), statistics.getSalary().getAverageGross());
        assertEquals(new BigDecimal("0.00"), statistics.getSalary().getMonthOverMonth());
        assertEquals(1, statistics.getSalary().getEmployees().size());
        assertEquals(new BigDecimal("11400.00"), statistics.getDepartment().getAverageSalaries().get(0).get("averageGross"));
    }

    @Test
    void jobAverageSalaryUsesTheSelectedMonthsEffectiveSalary() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("job-history.db");
        jdbc.update("INSERT INTO hr_department (department_code,department_name,description) VALUES ('D1','研发部','研发')");
        jdbc.update("INSERT INTO recruitment_job (job_code,job_title,department_name,requirements,responsibilities,publish_date) VALUES ('J1','工程师','研发部','要求','职责','2026-01-01')");
        insertEmployee(jdbc, "E001", "员工一", "110101199001010011", "13800000001", 1, new BigDecimal("15000.00"));
        jdbc.update("UPDATE hr_employee SET hire_date='2026-06-01' WHERE employee_code='E001'");
        insertEmployee(jdbc, "E002", "员工二", "110101199001010022", "13800000002", 1, new BigDecimal("15000.00"));
        jdbc.update("UPDATE hr_employee SET hire_date='2026-08-01' WHERE employee_code='E002'");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-01',0,10000),(1,'2026-08',10000,15000),(2,'2026-08',0,15000)");

        HrStatisticsServiceImpl service = new HrStatisticsServiceImpl(jdbc);

        assertEquals(new BigDecimal("10000.00"), service.statistics("2026-06").getRecruitment()
                .getJobAverageSalaries().get(0).get("averageBaseSalary"));
        assertEquals(new BigDecimal("15000.00"), service.statistics("2026-08").getRecruitment()
                .getJobAverageSalaries().get(0).get("averageBaseSalary"));
    }

    @Test
    void newEmployeeGrowthUsesBaseSalaryRatherThanPerformanceOrOvertime() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("new-employee-growth.db");
        jdbc.update("INSERT INTO hr_department (department_code,department_name,description) VALUES ('D1','研发部','研发')");
        jdbc.update("INSERT INTO recruitment_job (job_code,job_title,department_name,requirements,responsibilities,publish_date) VALUES ('J1','工程师','研发部','要求','职责','2026-01-01')");
        insertEmployee(jdbc, "E001", "新员工", "110101199001010011", "13800000001", 1, new BigDecimal("12000.00"));
        jdbc.update("UPDATE hr_employee SET hire_date='2026-07-01' WHERE employee_code='E001'");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-07',0,10000),(1,'2026-08',10000,12000)");
        jdbc.update("INSERT INTO hr_performance_month (employee_id,salary_month,amount) VALUES (1,'2026-08',3000)");
        jdbc.update("INSERT INTO hr_overtime_month (employee_id,salary_month,overtime_hours,unit_rate,overtime_pay) VALUES (1,'2026-08',10,100,1000)");

        HrStatisticsVO statistics = new HrStatisticsServiceImpl(jdbc).statistics("2026-08");

        assertEquals(new BigDecimal("16000.00"), statistics.getSalary().getEmployees().get(0).get("grossIncome"));
        assertEquals(new BigDecimal("20.00"), statistics.getSalary().getEmployees().get(0).get("newEmployeeGrowth"));
    }

    @Test
    void dismissalAverageExcludesUnconfirmedEmployeesWithoutDroppingTheirCount() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("dismissal-average.db");
        jdbc.update("INSERT INTO hr_department (department_code,department_name,description) VALUES ('D1','研发部','研发')");
        jdbc.update("INSERT INTO recruitment_job (job_code,job_title,department_name,requirements,responsibilities,publish_date) VALUES ('J1','工程师','研发部','要求','职责','2026-01-01')");
        insertEmployee(jdbc, "E001", "已确认员工", "110101199001010011", "13800000001", 1, new BigDecimal("10000.00"));
        insertEmployee(jdbc, "E002", "未确认员工", "110101199001010022", "13800000002", 0, new BigDecimal("0.00"));
        jdbc.update("UPDATE hr_employee SET employment_status=3,dismissal_date='2026-08-15',dismissal_reason='组织调整'");
        insertPayroll(jdbc, 1, "10000.00");
        insertPayroll(jdbc, 2, "99999.00");

        HrStatisticsVO statistics = new HrStatisticsServiceImpl(jdbc).statistics("2026-08");

        assertEquals(2, statistics.getDismissal().getCount());
        assertEquals(new BigDecimal("10000.00"), statistics.getDismissal().getAverageGross());
        assertEquals(2L, statistics.getDismissal().getReasons().get(0).get("value"));
    }

    @Test
    void departmentAveragesExcludeDisabledAndEmptyDepartments() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("department-filter.db");
        jdbc.update("INSERT INTO hr_department (department_code,department_name,description,status) VALUES ('D1','Active','Active',1)");
        jdbc.update("INSERT INTO hr_department (department_code,department_name,description,status) VALUES ('D2','Disabled','Disabled',0)");
        jdbc.update("INSERT INTO hr_department (department_code,department_name,description,status) VALUES ('D3','Empty','Empty',1)");
        jdbc.update("INSERT INTO recruitment_job (job_code,job_title,department_name,requirements,responsibilities,publish_date) VALUES ('J1','Engineer','Active','Requirements','Responsibilities','2026-01-01')");
        insertEmployee(jdbc, "E001", "Active Employee", "110101199001010011", "13800000001", 1, new BigDecimal("10000.00"));
        insertEmployee(jdbc, "E002", "Disabled Employee", "110101199001010022", "13800000002", 1, new BigDecimal("90000.00"));
        jdbc.update("UPDATE hr_employee SET department_id=2 WHERE employee_code='E002'");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-01',0,10000),(2,'2026-01',0,90000)");

        HrStatisticsVO statistics = new HrStatisticsServiceImpl(jdbc).statistics("2026-08");

        assertEquals(1, statistics.getDepartment().getAverageSalaries().size());
        assertEquals("Active", statistics.getDepartment().getAverageSalaries().get(0).get("departmentName"));
        assertEquals(new BigDecimal("1.00"), statistics.getDepartment().getAverageEmployeeCount());
        assertEquals(new BigDecimal("10000.00"), statistics.getDepartment().getAverageGrossSalary());
    }

    private JdbcTemplate migratedDatabase(String fileName) throws Exception {
        String url = "jdbc:sqlite:" + tempDirectory.resolve(fileName).toString().replace('\\', '/');
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        new DatabaseMigrationRunner(dataSource, new ActiveDatabase(DatabaseType.SQLITE, url, "", "", false), new AppMigrationProperties()).run();
        return new JdbcTemplate(dataSource);
    }

    private void insertEmployee(JdbcTemplate jdbc, String code, String name, String idCard, String mobile,
                                int confirmed, BigDecimal salary) {
        jdbc.update("INSERT INTO hr_employee (employee_code,full_name,id_card_no,mobile_phone,recruitment_major,position_name,department_id,bank_account_no,bank_name,hire_date,employment_status,job_id,base_salary,salary_confirmed) VALUES (?,?,?,?,?,?,?,?,?,'2026-01-01',1,1,?,?)",
                code, name, idCard, mobile, "计算机", "工程师", 1, "6222000000000000" + confirmed, "测试银行", salary, confirmed);
    }

    private void insertPayroll(JdbcTemplate jdbc, long employeeId, String grossIncome) {
        jdbc.update("INSERT INTO hr_payroll_month (employee_id,salary_month,base_salary,performance,overtime_hours,overtime_pay,gross_income,social_insurance_total,special_deduction_total,taxable_income_month,cumulative_income,cumulative_deduction_base,cumulative_social_insurance,cumulative_special_deduction,cumulative_taxable_income,cumulative_tax_withheld,current_tax_withheld,net_pay,locked) VALUES (?,'2026-08',?,0,0,0,?,0,0,0,?,40000,0,0,0,0,0,?,0)",
                employeeId, new BigDecimal(grossIncome), new BigDecimal(grossIncome),
                new BigDecimal(grossIncome), new BigDecimal(grossIncome));
    }
}
