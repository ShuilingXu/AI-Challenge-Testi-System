package com.autohr.modules.hr.service.impl;

import com.autohr.config.database.ActiveDatabase;
import com.autohr.config.database.AppMigrationProperties;
import com.autohr.config.database.DatabaseMigrationRunner;
import com.autohr.config.database.DatabaseType;
import com.autohr.modules.hr.dto.PayrollGenerateRequest;
import com.autohr.modules.hr.dto.PayrollVO;
import com.autohr.modules.hr.dto.MonthlyPerformanceRequest;
import com.autohr.common.exception.BusinessException;
import jakarta.validation.Validation;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayrollPersistenceIntegrationTest {
    @TempDir Path tempDirectory;

    @Test
    void currentTaxSubtractsPersistedPriorWithholding() throws Exception {
        String url = "jdbc:sqlite:" + tempDirectory.resolve("payroll.db").toString().replace('\\', '/');
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        new DatabaseMigrationRunner(dataSource, new ActiveDatabase(DatabaseType.SQLITE, url, "", "", false), new AppMigrationProperties()).run();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO hr_department (department_code,department_name,description) VALUES ('D1','研发部','研发')");
        jdbc.update("INSERT INTO recruitment_job (job_code,job_title,department_name,requirements,responsibilities,publish_date) VALUES ('J1','工程师','研发部','要求','职责','2026-01-01')");
        jdbc.update("INSERT INTO hr_employee (employee_code,full_name,id_card_no,mobile_phone,recruitment_major,position_name,department_id,bank_account_no,bank_name,hire_date,employment_status,job_id,base_salary,salary_confirmed) VALUES ('E001','测试员工','110101199001010011','13800000001','计算机','工程师',1,'6222000000000001','测试银行','2026-01-01',1,1,10000,1)");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-01',0,10000)");
        jdbc.update("INSERT INTO hr_payroll_month (employee_id,salary_month,base_salary,performance,overtime_hours,overtime_pay,gross_income,social_insurance_total,special_deduction_total,taxable_income_month,cumulative_income,cumulative_deduction_base,cumulative_social_insurance,cumulative_special_deduction,cumulative_taxable_income,cumulative_tax_withheld,current_tax_withheld,net_pay,locked) VALUES (1,'2026-01',10000,0,0,0,10000,0,0,5000,10000,5000,0,0,5000,150,100,9900,0)");
        PayrollServiceImpl service = new PayrollServiceImpl(jdbc, Validation.buildDefaultValidatorFactory().getValidator());
        PayrollGenerateRequest request = new PayrollGenerateRequest();
        request.setEmployeeId(1L);
        request.setSalaryMonth("2026-02");

        List<PayrollVO> payrolls = service.generate(request);

        assertEquals(new BigDecimal("300.00"), payrolls.get(0).getCumulativeTaxWithheld());
        assertEquals(new BigDecimal("200.00"), payrolls.get(0).getCurrentTaxWithheld());
        assertEquals(new BigDecimal("9800.00"), payrolls.get(0).getNetPay());
        assertEquals(new BigDecimal("20000.00"), payrolls.get(0).getCumulativeIncome());
        assertEquals(new BigDecimal("10000.00"), payrolls.get(0).getCumulativeDeductionBase());
    }

    @Test
    void singleMonthPayrollMatchesTheManualGrossTaxAndNetCalculation() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("single-month.db");
        insertDepartmentAndJob(jdbc);
        insertEmployee(jdbc, 1, "E001", "2026-08-01", 1, null, "20000.00");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-08',0,20000)");
        jdbc.update("INSERT INTO hr_performance_month (employee_id,salary_month,amount) VALUES (1,'2026-08',1000)");
        jdbc.update("INSERT INTO hr_overtime_month (employee_id,salary_month,overtime_hours,unit_rate,overtime_pay) VALUES (1,'2026-08',2,50,100)");
        jdbc.update("INSERT INTO hr_social_insurance_month (employee_id,salary_month,pension,medical,unemployment,housing_fund) VALUES (1,'2026-08',1000,200,50,750)");
        jdbc.update("INSERT INTO hr_special_deduction_month (employee_id,salary_month,children_education,continuing_education,housing_loan_interest,housing_rent,elderly_support,infant_care,other_deduction) VALUES (1,'2026-08',1000,0,0,0,0,0,0)");

        PayrollServiceImpl service = service(jdbc);
        PayrollVO payroll = service.generate(request(1L, "2026-08")).get(0);

        assertEquals(new BigDecimal("21100.00"), payroll.getGrossIncome());
        assertEquals(new BigDecimal("13100.00"), payroll.getCumulativeTaxableIncome());
        assertEquals(new BigDecimal("393.00"), payroll.getCurrentTaxWithheld());
        assertEquals(new BigDecimal("18707.00"), payroll.getNetPay());
        assertEquals("110***********0001", payroll.getIdCardNo());
        assertEquals("110***********0001", service.listPayroll("2026-08", 1L).get(0).getIdCardNo());
    }

    @Test
    void yearMidHireUsesOnlyEmployedMonthsInPersistedPayroll() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("mid-year.db");
        insertDepartmentAndJob(jdbc);
        insertEmployee(jdbc, 1, "E001", "2026-07-10", 1, null, "10000.00");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-07',0,10000)");

        PayrollVO payroll = service(jdbc).generate(request(1L, "2026-09")).get(0);

        assertEquals(new BigDecimal("30000.00"), payroll.getCumulativeIncome());
        assertEquals(new BigDecimal("15000.00"), payroll.getCumulativeDeductionBase());
        assertEquals(new BigDecimal("15000.00"), payroll.getCumulativeTaxableIncome());
        assertEquals(new BigDecimal("450.00"), payroll.getCurrentTaxWithheld());
    }

    @Test
    void fullMonthGenerationSkipsLockedPayrollAndGeneratesOtherEmployees() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("locked-batch.db");
        insertDepartmentAndJob(jdbc);
        insertEmployee(jdbc, 1, "E001", "2026-01-01", 1, null, "10000.00");
        insertEmployee(jdbc, 2, "E002", "2026-01-01", 1, null, "12000.00");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-01',0,10000),(2,'2026-01',0,12000)");
        insertPayroll(jdbc, 1, "2026-08", 1);

        List<PayrollVO> generated = service(jdbc).generate(request(null, "2026-08"));

        assertEquals(1, generated.size());
        assertEquals("E002", generated.get(0).getEmployeeCode());
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM hr_payroll_month WHERE salary_month='2026-08'", Long.class));
    }

    @Test
    void resignedEmployeeCanGenerateDismissalMonthButNotLater() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("dismissal-boundary.db");
        insertDepartmentAndJob(jdbc);
        insertEmployee(jdbc, 1, "E001", "2026-01-01", 3, "2026-08-15", "10000.00");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-01',0,10000)");

        assertEquals(1, service(jdbc).generate(request(1L, "2026-08")).size());
        assertThrows(BusinessException.class, () -> service(jdbc).generate(request(1L, "2026-09")));
    }

    @Test
    void fullMonthGenerationIncludesResignedEmployeeInDismissalMonth() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("dismissal-batch.db");
        insertDepartmentAndJob(jdbc);
        insertEmployee(jdbc, 1, "E001", "2026-01-01", 3, "2026-08-15", "10000.00");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-01',0,10000)");

        List<PayrollVO> generated = service(jdbc).generate(request(null, "2026-08"));

        assertEquals(1, generated.size());
        assertEquals("E001", generated.get(0).getEmployeeCode());
    }

    @Test
    void laterLockedPayrollBlocksRecalculationDeletionAndEarlierInputChanges() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("locked-future-payroll.db");
        insertDepartmentAndJob(jdbc);
        insertEmployee(jdbc, 1, "E001", "2026-01-01", 1, null, "10000.00");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-01',0,10000)");
        insertPayroll(jdbc, 1, "2026-08", 0);
        insertPayroll(jdbc, 1, "2026-09", 1);
        jdbc.update("INSERT INTO hr_performance_month (employee_id,salary_month,amount) VALUES (1,'2026-07',500)");
        PayrollServiceImpl payrollService = service(jdbc);

        BusinessException recalculateError = assertThrows(BusinessException.class,
                () -> payrollService.generate(request(1L, "2026-08")));
        BusinessException deleteError = assertThrows(BusinessException.class,
                () -> payrollService.deletePayroll(1L, "2026-08"));
        MonthlyPerformanceRequest performance = new MonthlyPerformanceRequest();
        performance.setEmployeeId(1L);
        performance.setSalaryMonth("2026-07");
        performance.setAmount(new BigDecimal("800.00"));
        BusinessException inputSaveError = assertThrows(BusinessException.class,
                () -> payrollService.savePerformance(performance, 9L));
        BusinessException inputDeleteError = assertThrows(BusinessException.class,
                () -> payrollService.deleteInput("performance", 1L, "2026-07"));

        String expected = "A later payroll is locked; unlock subsequent months before changing this payroll period";
        assertEquals(expected, recalculateError.getMessage());
        assertEquals(expected, deleteError.getMessage());
        assertEquals(expected, inputSaveError.getMessage());
        assertEquals(expected, inputDeleteError.getMessage());
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_payroll_month WHERE employee_id=1 AND salary_month='2026-08'",
                Long.class));
        assertEquals(new BigDecimal("500"), jdbc.queryForObject(
                "SELECT amount FROM hr_performance_month WHERE employee_id=1 AND salary_month='2026-07'",
                BigDecimal.class));
    }

    @Test
    void payrollExportWorkbookUsesItsHeaderOrder() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("export.db");
        insertDepartmentAndJob(jdbc);
        insertEmployee(jdbc, 1, "E001", "2026-01-01", 1, null, "10000.00");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-01',0,10000)");
        service(jdbc).generate(request(1L, "2026-08"));
        jdbc.update("UPDATE hr_payroll_month SET gross_income=9999999999.99 WHERE employee_id=1 AND salary_month='2026-08'");

        byte[] bytes = service(jdbc).exportPayroll("2026-08", null);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            String[] actual = new String[workbook.getSheetAt(0).getRow(0).getLastCellNum()];
            for (int index = 0; index < actual.length; index++) {
                actual[index] = workbook.getSheetAt(0).getRow(0).getCell(index).getStringCellValue();
            }
            assertArrayEquals(com.autohr.modules.hr.service.PayrollExportTemplate.HEADERS, actual);
            assertEquals("E001", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals("员工1", workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
            assertEquals("110101199001010001", workbook.getSheetAt(0).getRow(1).getCell(3).getStringCellValue());
            assertEquals(CellType.NUMERIC, workbook.getSheetAt(0).getRow(1).getCell(4).getCellType());
            assertEquals("9999999999.99", ((XSSFCell) workbook.getSheetAt(0).getRow(1).getCell(4)).getCTCell().getV());
            assertEquals(0D, workbook.getSheetAt(0).getRow(1).getCell(5).getNumericCellValue());
        }
    }

    @Test
    void backdatedPayrollUsesSalaryBeforeTheFirstEffectiveAdjustment() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("backdated-payroll.db");
        insertDepartmentAndJob(jdbc);
        insertEmployee(jdbc, 1, "E001", "2026-01-01", 1, null, "20000.00");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-06',10000,20000)");

        PayrollVO payroll = service(jdbc).generate(request(1L, "2026-05")).get(0);

        assertEquals(new BigDecimal("10000.00"), payroll.getBaseSalary());
    }

    @Test
    void concurrentGenerationKeepsOnePayrollRow() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("concurrent-generation.db");
        insertDepartmentAndJob(jdbc);
        insertEmployee(jdbc, 1, "E001", "2026-01-01", 1, null, "10000.00");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after) VALUES (1,'2026-01',0,10000)");
        PayrollServiceImpl payrollService = service(jdbc);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<PayrollVO>> first = executor.submit(() -> generateAfterBarrier(payrollService, ready, start));
            Future<List<PayrollVO>> second = executor.submit(() -> generateAfterBarrier(payrollService, ready, start));
            org.junit.jupiter.api.Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, first.get(10, TimeUnit.SECONDS).size());
            assertEquals(1, second.get(10, TimeUnit.SECONDS).size());
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_payroll_month WHERE employee_id=1 AND salary_month='2026-08'",
                Long.class));
    }

    @Test
    void monthlyInputListsExposeTheEmployeeNameExpectedByTheFrontend() throws Exception {
        JdbcTemplate jdbc = migratedDatabase("input-list.db");
        insertDepartmentAndJob(jdbc);
        insertEmployee(jdbc, 1, "E001", "2026-01-01", 1, null, "10000.00");
        jdbc.update("INSERT INTO hr_performance_month (employee_id,salary_month,amount) VALUES (1,'2026-08',1200)");

        List<java.util.Map<String, Object>> rows = service(jdbc).listInputs("performance", "2026-08", null);

        assertEquals(1, rows.size());
        assertFalse(rows.get(0).containsKey("operator_user_id"));
        assertEquals("员工1", rows.get(0).get("employee_name"));
    }

    private JdbcTemplate migratedDatabase(String fileName) throws Exception {
        String url = "jdbc:sqlite:" + tempDirectory.resolve(fileName).toString().replace('\\', '/');
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        new DatabaseMigrationRunner(dataSource, new ActiveDatabase(DatabaseType.SQLITE, url, "", "", false), new AppMigrationProperties()).run();
        return new JdbcTemplate(dataSource);
    }

    private PayrollServiceImpl service(JdbcTemplate jdbc) {
        return new PayrollServiceImpl(jdbc, Validation.buildDefaultValidatorFactory().getValidator());
    }

    private PayrollGenerateRequest request(Long employeeId, String month) {
        PayrollGenerateRequest request = new PayrollGenerateRequest();
        request.setEmployeeId(employeeId);
        request.setSalaryMonth(month);
        return request;
    }

    private List<PayrollVO> generateAfterBarrier(PayrollServiceImpl service, CountDownLatch ready,
                                                  CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent generation barrier timed out");
            }
            return service.generate(request(1L, "2026-08"));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private void insertDepartmentAndJob(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO hr_department (department_code,department_name,description) VALUES ('D1','研发部','研发')");
        jdbc.update("INSERT INTO recruitment_job (job_code,job_title,department_name,requirements,responsibilities,publish_date) VALUES ('J1','工程师','研发部','要求','职责','2026-01-01')");
    }

    private void insertEmployee(JdbcTemplate jdbc, int sequence, String code, String hireDate,
                                int status, String dismissalDate, String salary) {
        jdbc.update("INSERT INTO hr_employee (employee_code,full_name,id_card_no,mobile_phone,recruitment_major,position_name,department_id,bank_account_no,bank_name,hire_date,employment_status,job_id,base_salary,salary_confirmed,dismissal_date) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                code, "员工" + sequence, "1101011990010100" + String.format("%02d", sequence),
                "138000000" + String.format("%02d", sequence), "计算机", "工程师", 1,
                "62220000000000" + String.format("%02d", sequence), "测试银行", hireDate, status, 1,
                new BigDecimal(salary), 1, dismissalDate);
    }

    private void insertPayroll(JdbcTemplate jdbc, long employeeId, String month, int locked) {
        jdbc.update("INSERT INTO hr_payroll_month (employee_id,salary_month,base_salary,performance,overtime_hours,overtime_pay,gross_income,social_insurance_total,special_deduction_total,taxable_income_month,cumulative_income,cumulative_deduction_base,cumulative_social_insurance,cumulative_special_deduction,cumulative_taxable_income,cumulative_tax_withheld,current_tax_withheld,net_pay,locked) VALUES (?,?,10000,0,0,0,10000,0,0,5000,80000,40000,0,0,40000,1480,150,9850,?)",
                employeeId, month, locked);
    }
}
