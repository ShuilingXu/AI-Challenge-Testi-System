package com.autohr.config.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseMigrationRunnerTest {
    @TempDir Path directory;

    @Test
    void renamesLegacyTableWithoutLosingRowsAndCanRunAgain() throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("legacy.db");
        DriverManagerDataSource source = new DriverManagerDataSource(url);
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE recruitment_job (id INTEGER PRIMARY KEY, job_code VARCHAR(64) NOT NULL UNIQUE, "
                + "job_title VARCHAR(128) NOT NULL, department_name VARCHAR(128) NOT NULL, "
                + "requirements VARCHAR(2000) NOT NULL, responsibilities VARCHAR(2000) NOT NULL, "
                + "publish_date DATE NOT NULL, status INTEGER NOT NULL DEFAULT 1, "
                + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO recruitment_job(id,job_code,job_title,department_name,requirements,responsibilities,publish_date) "
                + "VALUES(1,'EX1','考试一','一班','','','2026-09-30')");
        jdbc.execute("CREATE TABLE hr_employee (id INTEGER PRIMARY KEY, job_id INTEGER REFERENCES recruitment_job(id), full_name VARCHAR(64))");
        jdbc.update("INSERT INTO hr_employee(id,job_id,full_name) VALUES(7,1,'历史员工')");
        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(source,
                new ActiveDatabase(DatabaseType.SQLITE, url, "", "", false), new AppMigrationProperties());
        runner.run();
        runner.run();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM school_assessment_config WHERE id=1 AND job_code='EX1'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='recruitment_job'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM hr_employee WHERE id=7 AND job_id=1", Integer.class));
        assertEquals("school_assessment_config", jdbc.queryForObject("SELECT \"table\" FROM pragma_foreign_key_list('hr_employee') WHERE \"from\"='job_id'", String.class));
    }
}
