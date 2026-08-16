package com.autohr.config.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigrationRunnerTest {

    @TempDir
    Path tempDirectory;

    @Test
    void createsBusinessKeyIndexesAndRejectsHistoricalDuplicates() throws Exception {
        String url = "jdbc:sqlite:" + tempDirectory.resolve("migration.db").toString().replace('\\', '/');
        DataSource dataSource = new DriverManagerDataSource(url);
        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(
                dataSource,
                new ActiveDatabase(DatabaseType.SQLITE, url, "", "", false),
                new AppMigrationProperties());

        runner.run();

        try (Connection connection = dataSource.getConnection()) {
            assertUniqueIndex(connection, "hr_employee", "uq_hr_employee_source_candidate_id");
            assertUniqueIndex(connection, "recruitment_candidate", "uq_recruitment_candidate_job_interviewee");
            assertUniqueIndex(connection, "interview_process", "uq_interview_process_candidate_id");

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DROP INDEX uq_recruitment_candidate_job_interviewee");
                statement.executeUpdate("INSERT INTO recruitment_job "
                        + "(job_code, job_title, department_name, headcount, requirements, responsibilities, publish_date, status) "
                        + "VALUES ('JOB-1', 'Engineer', 'Technology', 1, 'Requirements', 'Responsibilities', '2026-08-14', 1)");
                statement.executeUpdate("INSERT INTO recruitment_candidate "
                        + "(job_id, full_name, mobile_phone, major, application_status, interview_stage_status, interviewee_user_id) "
                        + "VALUES (1, 'First', '13800138001', 'Computer Science', 'SUBMITTED', 'Review', 99)");
                statement.executeUpdate("INSERT INTO recruitment_candidate "
                        + "(job_id, full_name, mobile_phone, major, application_status, interview_stage_status, interviewee_user_id) "
                        + "VALUES (1, 'Second', '13800138002', 'Computer Science', 'SUBMITTED', 'Review', 99)");
            }
        }

        IllegalStateException error = assertThrows(IllegalStateException.class, runner::run);
        assertTrue(error.getMessage().contains("candidate applications"));
    }

    @Test
    void createsPayrollSchemaWithConstraintsAndIsIdempotent() throws Exception {
        String url = "jdbc:sqlite:" + tempDirectory.resolve("payroll-migration.db").toString().replace('\\', '/');
        DataSource dataSource = new DriverManagerDataSource(url);
        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(
                dataSource,
                new ActiveDatabase(DatabaseType.SQLITE, url, "", "", false),
                new AppMigrationProperties());

        runner.run();
        runner.run();

        try (Connection connection = dataSource.getConnection()) {
            assertColumn(connection, "hr_employee", "job_id");
            assertColumn(connection, "hr_employee", "base_salary");
            assertColumn(connection, "hr_employee", "salary_confirmed");
            assertColumn(connection, "recruitment_job", "default_overtime_rate");
            assertUniqueIndex(connection, "hr_salary_history", "uq_salary_history_employee_month");
            assertUniqueIndex(connection, "hr_performance_month", "uq_performance_employee_month");
            assertUniqueIndex(connection, "hr_overtime_month", "uq_overtime_employee_month");
            assertUniqueIndex(connection, "hr_social_insurance_month", "uq_social_employee_month");
            assertUniqueIndex(connection, "hr_special_deduction_month", "uq_special_employee_month");
            assertUniqueIndex(connection, "hr_payroll_month", "uq_payroll_employee_month");
            assertForeignKey(connection, "hr_employee", "job_id", "recruitment_job");
            assertForeignKey(connection, "hr_payroll_month", "employee_id", "hr_employee");
        }
    }

    @Test
    void archivesHistoricalVideoSessionDuplicatesBeforeAddingScopeUniqueness() throws Exception {
        String url = "jdbc:sqlite:" + tempDirectory.resolve("video-session-migration.db").toString().replace('\\', '/');
        DataSource dataSource = new DriverManagerDataSource(url);
        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(
                dataSource,
                new ActiveDatabase(DatabaseType.SQLITE, url, "", "", false),
                new AppMigrationProperties());
        runner.run();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP INDEX uq_interview_video_session_process_scope");
            statement.executeUpdate("INSERT INTO interview_video_session "
                    + "(process_id,process_stage_id,stage_scope_id,video_serial_no,video_join_link,session_status) VALUES "
                    + "(10,NULL,0,'legacy-old','/legacy-old','RECORDING'),"
                    + "(10,NULL,0,'legacy-new','/legacy-new','END_REQUESTED'),"
                    + "(20,101,101,'template-old','/template-old','RECORDING'),"
                    + "(20,101,101,'template-new','/template-new','END_REQUESTED')");
            statement.executeUpdate("INSERT INTO interview_video_session "
                    + "(process_id,process_stage_id,stage_scope_id,video_serial_no,video_join_link,session_status,"
                    + "merged_recording_path,transcript_text) VALUES "
                    + "(30,NULL,0,'complete-old','/complete-old','RECORDED','merged.webm','recoverable transcript'),"
                    + "(30,NULL,0,'empty-new','/empty-new','CREATED',NULL,NULL)");
        }

        runner.run();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertUniqueIndex(connection, "interview_video_session", "uq_interview_video_session_process_scope");
            try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM interview_video_session")) {
                assertTrue(rows.next());
                assertEquals(3, rows.getInt(1));
            }
            try (ResultSet archived = statement.executeQuery(
                    "SELECT COUNT(*) FROM interview_video_session_duplicate_archive")) {
                assertTrue(archived.next());
                assertEquals(3, archived.getInt(1));
            }
            try (ResultSet retained = statement.executeQuery(
                    "SELECT video_serial_no,transcript_text FROM interview_video_session WHERE process_id=30")) {
                assertTrue(retained.next());
                assertEquals("complete-old", retained.getString("video_serial_no"));
                assertEquals("recoverable transcript", retained.getString("transcript_text"));
            }
            try (ResultSet archived = statement.executeQuery(
                    "SELECT video_join_link,session_status FROM interview_video_session_duplicate_archive "
                            + "WHERE video_serial_no='empty-new'")) {
                assertTrue(archived.next());
                assertEquals("/empty-new", archived.getString("video_join_link"));
                assertEquals("CREATED", archived.getString("session_status"));
            }
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO interview_video_session "
                    + "(process_id,process_stage_id,stage_scope_id,video_serial_no,video_join_link,session_status) "
                    + "VALUES (10,NULL,0,'legacy-third','/legacy-third','CREATED')"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO interview_video_session "
                    + "(process_id,process_stage_id,stage_scope_id,video_serial_no,video_join_link,session_status) "
                    + "VALUES (40,200,0,'bad-scope','/bad-scope','CREATED')"));
        }
    }

    @Test
    void upgradesLegacySqliteEmployeesAndEnforcesTheJobForeignKey() throws Exception {
        String url = "jdbc:sqlite:" + tempDirectory.resolve("legacy-payroll.db").toString().replace('\\', '/');
        DataSource dataSource = new DriverManagerDataSource(url);
        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(
                dataSource,
                new ActiveDatabase(DatabaseType.SQLITE, url, "", "", false),
                new AppMigrationProperties());
        runner.run();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=OFF");
            statement.executeUpdate("INSERT INTO hr_department (department_code,department_name,description) "
                    + "VALUES ('D1','研发部','研发')");
            statement.executeUpdate("INSERT INTO recruitment_job "
                    + "(job_code,job_title,department_name,requirements,responsibilities,publish_date) "
                    + "VALUES ('J1','工程师','研发部','要求','职责','2026-01-01')");
            statement.executeUpdate("DROP TABLE hr_employee");
            statement.executeUpdate("CREATE TABLE hr_employee ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, employee_code VARCHAR(64) NOT NULL UNIQUE, "
                    + "full_name VARCHAR(64) NOT NULL, id_card_no VARCHAR(32) NOT NULL UNIQUE, "
                    + "mobile_phone VARCHAR(32) NOT NULL UNIQUE, email VARCHAR(128), "
                    + "recruitment_major VARCHAR(128) NOT NULL, position_name VARCHAR(128) NOT NULL, "
                    + "manager_employee_id INTEGER, department_id INTEGER NOT NULL, "
                    + "bank_account_no VARCHAR(64) NOT NULL, bank_name VARCHAR(128) NOT NULL, "
                    + "hire_date DATE NOT NULL, employment_status INTEGER NOT NULL DEFAULT 0, "
                    + "source_candidate_id INTEGER, interview_stage_status VARCHAR(64), source_channel VARCHAR(64), "
                    + "notes VARCHAR(1000), created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO hr_employee "
                    + "(employee_code,full_name,id_card_no,mobile_phone,recruitment_major,position_name,department_id,"
                    + "bank_account_no,bank_name,hire_date,employment_status) VALUES "
                    + "('E001','存量员工','110101199001010011','13800000001','计算机','工程师',1,"
                    + "'6222000000000001','测试银行','2025-01-01',1)");
        }

        runner.run();
        runner.run();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertForeignKey(connection, "hr_employee", "job_id", "recruitment_job");
            try (ResultSet employee = statement.executeQuery(
                    "SELECT job_id,base_salary,salary_confirmed FROM hr_employee WHERE employee_code='E001'")) {
                assertTrue(employee.next());
                assertEquals(1L, employee.getLong("job_id"));
                assertEquals("0", employee.getBigDecimal("base_salary").stripTrailingZeros().toPlainString());
                assertEquals(0, employee.getInt("salary_confirmed"));
            }
            statement.execute("PRAGMA foreign_keys=ON");
            assertThrows(SQLException.class, () -> statement.executeUpdate("UPDATE hr_employee SET job_id=999 WHERE employee_code='E001'"));
        }
    }

    @Test
    void rendersPayrollSchemaForMysqlAndPostgres() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/schema.sql"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");

        String mysql = renderDialect(DatabaseType.MYSQL, schema);
        assertTrue(mysql.contains("CREATE TABLE IF NOT EXISTS hr_payroll_month"));
        assertTrue(mysql.contains("INTEGER PRIMARY KEY AUTO_INCREMENT"));
        assertTrue(mysql.contains("default_overtime_rate DECIMAL(12,2) NOT NULL DEFAULT 0"));
        assertTrue(mysql.contains("cumulative_income DECIMAL(16,2) NOT NULL"));
        assertTrue(mysql.contains("question_content TEXT NOT NULL"));
        assertTrue(mysql.contains("suggested_next_question TEXT"));
        assertTrue(mysql.contains("CREATE UNIQUE INDEX uq_payroll_employee_month"));
        assertTrue(mysql.contains("CREATE UNIQUE INDEX uq_interview_video_session_process_scope"));
        assertFalse(mysql.contains("AUTOINCREMENT"));
        assertFalse(employeeTable(mysql).contains("FOREIGN KEY (job_id) REFERENCES recruitment_job(id)"));

        String postgres = renderDialect(DatabaseType.PGSQL, schema);
        assertTrue(postgres.contains("CREATE TABLE IF NOT EXISTS hr_payroll_month"));
        assertTrue(postgres.contains("INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY"));
        assertTrue(postgres.contains("calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"));
        assertTrue(postgres.contains("cumulative_taxable_income DECIMAL(16,2) NOT NULL"));
        assertTrue(postgres.contains("CREATE UNIQUE INDEX IF NOT EXISTS uq_interview_video_session_process_scope"));
        assertFalse(postgres.contains("AUTOINCREMENT"));
        assertFalse(postgres.contains("DATETIME"));
        assertFalse(employeeTable(postgres).contains("FOREIGN KEY (job_id) REFERENCES recruitment_job(id)"));
    }

    private String renderDialect(DatabaseType type, String schema) throws Exception {
        String url = "jdbc:sqlite:" + tempDirectory.resolve(type.name().toLowerCase() + ".db")
                .toString().replace('\\', '/');
        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(
                new DriverManagerDataSource(url),
                new ActiveDatabase(type, url, "", "", false),
                new AppMigrationProperties());
        Method method = DatabaseMigrationRunner.class.getDeclaredMethod("toDialect", String.class);
        method.setAccessible(true);
        return (String) method.invoke(runner, schema);
    }

    private String employeeTable(String schema) {
        return schema.substring(schema.indexOf("CREATE TABLE IF NOT EXISTS hr_employee"),
                schema.indexOf("CREATE TABLE IF NOT EXISTS recruitment_job"));
    }

    private void assertUniqueIndex(Connection connection, String table, String expectedName) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet indexes = statement.executeQuery("PRAGMA index_list('" + table + "')")) {
            boolean found = false;
            while (indexes.next()) {
                if (expectedName.equalsIgnoreCase(indexes.getString("name"))) {
                    assertEquals(1, indexes.getInt("unique"));
                    found = true;
                }
            }
            assertTrue(found, "Missing unique index " + expectedName);
        }
    }

    private void assertColumn(Connection connection, String table, String expectedColumn) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info('" + table + "')")) {
            boolean found = false;
            while (columns.next()) found |= expectedColumn.equalsIgnoreCase(columns.getString("name"));
            assertTrue(found, "Missing column " + table + "." + expectedColumn);
        }
    }

    private void assertForeignKey(Connection connection, String table, String column, String referencedTable) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet keys = statement.executeQuery("PRAGMA foreign_key_list('" + table + "')")) {
            boolean found = false;
            while (keys.next()) {
                found |= column.equalsIgnoreCase(keys.getString("from"))
                        && referencedTable.equalsIgnoreCase(keys.getString("table"));
            }
            assertTrue(found, "Missing foreign key " + table + "." + column + " -> " + referencedTable);
        }
    }
}
