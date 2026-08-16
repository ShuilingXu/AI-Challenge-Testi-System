package com.autohr.modules.hr.service.impl;

import com.autohr.common.exception.BusinessException;
import com.autohr.config.database.ActiveDatabase;
import com.autohr.config.database.AppMigrationProperties;
import com.autohr.config.database.DatabaseMigrationRunner;
import com.autohr.config.database.DatabaseType;
import com.autohr.modules.hr.dto.PayrollGenerateRequest;
import com.autohr.modules.hr.dto.PayrollVO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollDatabaseDialectIntegrationTest {

    @BeforeAll
    static void useDockerApiSupportedByCurrentDockerDesktop() {
        System.setProperty("api.version", System.getProperty("api.version", "1.44"));
    }

    @Test
    void postgresMigrationAndPayrollUpsertAreConcurrencySafe() throws Exception {
        requireDocker();
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            verifyDialect(database, DatabaseType.PGSQL);
        }
    }

    @Test
    void mysqlMigrationAndPayrollUpsertAreConcurrencySafe() throws Exception {
        requireDocker();
        try (MySQLContainer<?> database = new MySQLContainer<>("mysql:8.4")) {
            verifyDialect(database, DatabaseType.MYSQL);
        }
    }

    private void verifyDialect(JdbcDatabaseContainer<?> database, DatabaseType databaseType) throws Exception {
        database.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                database.getJdbcUrl(), database.getUsername(), database.getPassword());
        ActiveDatabase activeDatabase = new ActiveDatabase(
                databaseType, database.getJdbcUrl(), database.getUsername(), database.getPassword(), false);
        new DatabaseMigrationRunner(dataSource, activeDatabase, new AppMigrationProperties()).run();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertPayrollFixture(jdbc);

        PayrollServiceImpl service = new PayrollServiceImpl(
                jdbc, Validation.buildDefaultValidatorFactory().getValidator());
        service.configureDatabase(activeDatabase);
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        runConcurrentGeneration(service, transactions);

        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_payroll_month WHERE employee_id=1 AND salary_month='2026-08'",
                Long.class));

        PayrollVO attemptedReplacement = service.listPayroll("2026-08", 1L).get(0);
        jdbc.update("UPDATE hr_payroll_month SET locked=1 WHERE employee_id=1 AND salary_month='2026-08'");
        attemptedReplacement.setBaseSalary(new BigDecimal("99999.99"));
        assertThrows(BusinessException.class, () -> service.upsertPayroll(attemptedReplacement));
        assertEquals(new BigDecimal("10000.00"), jdbc.queryForObject(
                "SELECT base_salary FROM hr_payroll_month WHERE employee_id=1 AND salary_month='2026-08'",
                BigDecimal.class));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM hr_employee WHERE id=1"));
    }

    private void runConcurrentGeneration(PayrollServiceImpl service, TransactionTemplate transactions) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<PayrollVO>> first = executor.submit(() -> generateAfterBarrier(service, transactions, ready, start));
            Future<List<PayrollVO>> second = executor.submit(() -> generateAfterBarrier(service, transactions, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, first.get(15, TimeUnit.SECONDS).size());
            assertEquals(1, second.get(15, TimeUnit.SECONDS).size());
        } finally {
            executor.shutdownNow();
        }
    }

    private List<PayrollVO> generateAfterBarrier(PayrollServiceImpl service, TransactionTemplate transactions,
                                                  CountDownLatch ready,
                                                  CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent generation barrier timed out");
            }
            PayrollGenerateRequest request = new PayrollGenerateRequest();
            request.setEmployeeId(1L);
            request.setSalaryMonth("2026-08");
            return transactions.execute(status -> service.generate(request));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private void insertPayrollFixture(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO hr_department (id,department_code,department_name,description) "
                + "VALUES (1,'D1','Engineering','Engineering')");
        jdbc.update("INSERT INTO recruitment_job (id,job_code,job_title,department_name,requirements,"
                + "responsibilities,publish_date) VALUES (1,'J1','Engineer','Engineering','Requirements',"
                + "'Responsibilities','2026-01-01')");
        jdbc.update("INSERT INTO hr_employee (id,employee_code,full_name,id_card_no,mobile_phone,"
                + "recruitment_major,position_name,department_id,bank_account_no,bank_name,hire_date,"
                + "employment_status,job_id,base_salary,salary_confirmed) VALUES "
                + "(1,'E001','Employee 1','110101199001010001','13800000001','Computer Science',"
                + "'Engineer',1,'6222000000000001','Test Bank','2026-01-01',1,1,10000,1)");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,"
                + "base_salary_after) VALUES (1,'2026-01',0,10000)");
    }

    private void requireDocker() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is required for MySQL/PostgreSQL dialect integration tests");
    }
}
