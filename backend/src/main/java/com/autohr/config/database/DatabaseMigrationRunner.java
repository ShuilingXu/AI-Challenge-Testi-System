package com.autohr.config.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@EnableConfigurationProperties(AppMigrationProperties.class)
public class DatabaseMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationRunner.class);
    private static final long POSTGRES_MIGRATION_LOCK_ID = 4_154_857_282_026L;
    private static final String MYSQL_MIGRATION_LOCK_NAME = "autohr_schema_migration";
    private static final List<String> PRIMARY_KEY_TABLES = List.of(
            "recruitment_job", "recruitment_candidate",
            "interview_batch", "interview_question", "interview_candidate", "interview_submission",
            "sys_user", "sys_audit_log",
            "interview_knowledge_base", "interview_knowledge_item", "interview_job_knowledge_weight",
            "interview_llm_config", "interview_process", "interview_ai_record", "interview_video_session",
            "interview_process_template", "interview_process_template_stage", "interview_process_stage"
            , "school_class", "school_student", "school_exam", "school_exam_attempt"
    );

    private final DataSource dataSource;
    private final ActiveDatabase activeDatabase;
    private final AppMigrationProperties properties;

    public DatabaseMigrationRunner(DataSource dataSource, ActiveDatabase activeDatabase, AppMigrationProperties properties) {
        this.dataSource = dataSource;
        this.activeDatabase = activeDatabase;
        this.properties = properties;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!properties.isEnabled()) {
            log.info("Database migration disabled");
            return;
        }
        List<String> statements = loadStatements();
        try (Connection connection = dataSource.getConnection()) {
            acquireMigrationLock(connection);
            try (Statement statement = connection.createStatement()) {
                retireLegacyHrData(statement);
                for (String sql : statements.stream().filter(sql -> !isCreateIndex(sql)).toList()) {
                    execute(statement, sql);
                }
                migrateInterviewProcessColumns(connection, statement);
                migrateInterviewProcessTemplateColumns(connection, statement);
                migrateInterviewAiRecordColumns(connection, statement);
                migrateInterviewVideoSessionColumns(connection, statement);
                migrateInterviewProcessStageColumns(connection, statement);
                migrateInterviewLlmConfigColumns(connection, statement);
                migrateSysUserColumns(connection, statement);
                migrateReferentialIntegrityConstraints(connection, statement);
                assertNoDuplicateBusinessKeys(statement);
                migrateDatabaseGeneratedPrimaryKeys(connection, statement);
                for (String sql : statements.stream().filter(this::isCreateIndex).toList()) {
                    execute(statement, sql);
                }
            } finally {
                releaseMigrationLock(connection);
            }
        }
        log.info("Database migration completed for {}", activeDatabase.type());
    }

    private List<String> loadStatements() throws IOException {
        String schema = new ClassPathResource("schema.sql")
                .getContentAsString(StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        return Arrays.stream(toDialect(schema).split(";"))
                .map(String::trim)
                .filter(sql -> !sql.isEmpty())
                .toList();
    }

    private String toDialect(String schema) {
        return switch (activeDatabase.type()) {
            case SQLITE -> schema;
            case MYSQL -> schema
                    .replace("    FOREIGN KEY (department_id) REFERENCES hr_department(id),\n    FOREIGN KEY (job_id) REFERENCES recruitment_job(id)\n);",
                            "    FOREIGN KEY (department_id) REFERENCES hr_department(id)\n);")
                    .replace("VARCHAR(5000) NOT NULL", "TEXT NOT NULL")
                    .replace("VARCHAR(5000)", "TEXT")
                    .replace("CREATE UNIQUE INDEX IF NOT EXISTS", "CREATE UNIQUE INDEX")
                    .replace("CREATE INDEX IF NOT EXISTS", "CREATE INDEX")
                    .replace("INTEGER PRIMARY KEY AUTOINCREMENT", "INTEGER PRIMARY KEY AUTO_INCREMENT");
            case PGSQL -> schema
                    .replace("    FOREIGN KEY (department_id) REFERENCES hr_department(id),\n    FOREIGN KEY (job_id) REFERENCES recruitment_job(id)\n);",
                            "    FOREIGN KEY (department_id) REFERENCES hr_department(id)\n);")
                    .replace("INTEGER PRIMARY KEY AUTOINCREMENT", "INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY")
                    .replace("DATETIME", "TIMESTAMP");
        };
    }

    private void execute(Statement statement, String sql) throws SQLException {
        try {
            statement.executeUpdate(sql);
        } catch (SQLException ex) {
            if (isIgnorable(sql, ex)) {
                log.debug("Skipping existing migration object: {}", firstLine(sql));
                return;
            }
            throw ex;
        }
    }

    private boolean isIgnorable(String sql, SQLException ex) {
        String normalized = sql.stripLeading().toUpperCase();
        if (isCreateIndex(sql)) {
            String state = ex.getSQLState();
            int errorCode = ex.getErrorCode();
            return "42S11".equals(state)
                    || "42710".equals(state)
                    || errorCode == 1061
                    || messageContains(ex, "already exists")
                    || messageContains(ex, "Duplicate key name");
        }
        if (normalized.startsWith("ALTER TABLE") && normalized.contains(" ADD COLUMN ")) {
            return "42701".equals(ex.getSQLState())
                    || "42S21".equals(ex.getSQLState())
                    || ex.getErrorCode() == 1060
                    || messageContains(ex, "already exists")
                    || messageContains(ex, "duplicate column");
        }
        return false;
    }

    /** Payroll and employee records belong to the retired HR product, never to school exams. */
    private void retireLegacyHrData(Statement statement) throws SQLException {
        try {
            statement.executeUpdate("DELETE FROM recruitment_resume_file");
            statement.executeUpdate("UPDATE recruitment_candidate SET id_card_no=NULL, email=NULL, expected_salary=NULL, "
                    + "self_introduction=NULL, resume_file_id=NULL, resume_llm_score=NULL, resume_llm_comment=NULL, "
                    + "resume_llm_status=NULL, resume_llm_evaluated_at=NULL");
        } catch (SQLException ignored) {
            // Fresh examination schema has already removed recruitment-only columns and tables.
        }
        for (String table : List.of("hr_payroll_month", "hr_special_deduction_month", "hr_social_insurance_month",
                "hr_overtime_month", "hr_performance_month", "hr_salary_history", "hr_integration_binding")) {
            statement.executeUpdate("DROP TABLE IF EXISTS " + table);
        }
        try {
            statement.executeUpdate("UPDATE hr_department SET manager_employee_id=NULL");
        } catch (SQLException ignored) {
            // New examination-only databases no longer contain an HR department table.
        }
        try {
            statement.executeUpdate("DELETE FROM hr_employee");
        } catch (SQLException ignored) {
            // The table is absent for new databases; its historical rows are removed when present.
        }
    }

    private boolean isCreateIndex(String sql) {
        String normalized = sql.stripLeading().toUpperCase();
        return normalized.startsWith("CREATE INDEX") || normalized.startsWith("CREATE UNIQUE INDEX");
    }

    private void migrateInterviewProcessColumns(Connection connection, Statement statement) throws SQLException {
        addColumnIfMissing(connection, statement, "interview_process", "ai_follow_up_threshold", "INTEGER NOT NULL DEFAULT 70");
        addColumnIfMissing(connection, statement, "interview_process", "ai_min_question_rounds", "INTEGER NOT NULL DEFAULT 5");
        addColumnIfMissing(connection, statement, "interview_process", "ai_max_question_rounds", "INTEGER NOT NULL DEFAULT 10");
        addColumnIfMissing(connection, statement, "interview_process", "anti_cheat_switch_limit", "INTEGER NOT NULL DEFAULT 5");
        addColumnIfMissing(connection, statement, "interview_process", "anti_cheat_switch_count", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, statement, "interview_process", "ai_output_mode", "VARCHAR(16) NOT NULL DEFAULT 'NORMAL'");
        addColumnIfMissing(connection, statement, "interview_process", "remark", "VARCHAR(2000)");
        addColumnIfMissing(connection, statement, "interview_process", "ai_recording_path", "VARCHAR(500)");
        addColumnIfMissing(connection, statement, "interview_process", "ai_recording_file_name", "VARCHAR(255)");
        addColumnIfMissing(connection, statement, "interview_process", "last_heartbeat_at", dateTimeType());
        addColumnIfMissing(connection, statement, "interview_process", "template_id", "INTEGER");
        addColumnIfMissing(connection, statement, "interview_process", "template_name", "VARCHAR(128)");
    }

    private void migrateInterviewProcessTemplateColumns(Connection connection, Statement statement) throws SQLException {
        addColumnIfMissing(connection, statement, "interview_process_template", "version", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, statement, "interview_process_template_stage", "round_knowledge_points", "VARCHAR(2000)");
    }

    private void migrateInterviewAiRecordColumns(Connection connection, Statement statement) throws SQLException {
        addColumnIfMissing(connection, statement, "interview_ai_record", "process_stage_id", "INTEGER");
        addColumnIfMissing(connection, statement, "interview_ai_record", "stage_scope_id", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, statement, "interview_ai_record", "question_status", "VARCHAR(32) NOT NULL DEFAULT 'READY'");
        addColumnIfMissing(connection, statement, "interview_ai_record", "question_generation_attempts", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, statement, "interview_ai_record", "question_generation_token", "VARCHAR(64)");
        addColumnIfMissing(connection, statement, "interview_ai_record", "question_lease_expires_at", dateTimeType());
        addColumnIfMissing(connection, statement, "interview_ai_record", "question_next_retry_at", dateTimeType());
        addColumnIfMissing(connection, statement, "interview_ai_record", "question_generation_error", "VARCHAR(1000)");
        addColumnIfMissing(connection, statement, "interview_ai_record", "previous_record_id", "INTEGER");
        addColumnIfMissing(connection, statement, "interview_ai_record", "suggested_next_question", "VARCHAR(5000)");
        addColumnIfMissing(connection, statement, "interview_ai_record", "answer_status", "VARCHAR(32) NOT NULL DEFAULT 'PENDING'");
        addColumnIfMissing(connection, statement, "interview_ai_record", "answer_processing_token", "VARCHAR(64)");
        addColumnIfMissing(connection, statement, "interview_ai_record", "answer_lease_expires_at", dateTimeType());
        addColumnIfMissing(connection, statement, "interview_ai_record", "answer_processing_attempts", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, statement, "interview_ai_record", "answer_error", "VARCHAR(1000)");
        addColumnIfMissing(connection, statement, "interview_ai_record", "interviewer_comment", "VARCHAR(2000)");
        statement.executeUpdate("UPDATE interview_ai_record SET stage_scope_id = COALESCE(process_stage_id, 0)");
        statement.executeUpdate("UPDATE interview_ai_record SET question_status = COALESCE(question_status, 'READY')");
        statement.executeUpdate("UPDATE interview_ai_record SET answer_status = CASE "
                + "WHEN answer_content IS NOT NULL AND average_score IS NOT NULL THEN 'COMPLETED' "
                + "WHEN answer_content IS NOT NULL THEN 'FAILED' ELSE COALESCE(answer_status, 'PENDING') END");
    }

    private void migrateInterviewVideoSessionColumns(Connection connection, Statement statement) throws SQLException {
        addColumnIfMissing(connection, statement, "interview_video_session", "approver_user_id", "INTEGER");
        addColumnIfMissing(connection, statement, "interview_video_session", "approver_name", "VARCHAR(64)");
        addColumnIfMissing(connection, statement, "interview_video_session", "interviewee_join_time", dateTimeType());
        addColumnIfMissing(connection, statement, "interview_video_session", "hr_join_time", dateTimeType());
        addColumnIfMissing(connection, statement, "interview_video_session", "start_time", dateTimeType());
        addColumnIfMissing(connection, statement, "interview_video_session", "end_time", dateTimeType());
        addColumnIfMissing(connection, statement, "interview_video_session", "recording_end_requested_at", dateTimeType());
        addColumnIfMissing(connection, statement, "interview_video_session", "recording_path", "VARCHAR(500)");
        addColumnIfMissing(connection, statement, "interview_video_session", "hr_recording_path", "VARCHAR(500)");
        addColumnIfMissing(connection, statement, "interview_video_session", "hr_recording_file_name", "VARCHAR(255)");
        addColumnIfMissing(connection, statement, "interview_video_session", "interviewee_recording_path", "VARCHAR(500)");
        addColumnIfMissing(connection, statement, "interview_video_session", "interviewee_recording_file_name", "VARCHAR(255)");
        addColumnIfMissing(connection, statement, "interview_video_session", "merged_recording_path", "VARCHAR(500)");
        addColumnIfMissing(connection, statement, "interview_video_session", "merged_recording_file_name", "VARCHAR(255)");
        addColumnIfMissing(connection, statement, "interview_video_session", "audio_path", "VARCHAR(500)");
        addColumnIfMissing(connection, statement, "interview_video_session", "audio_file_name", "VARCHAR(255)");
        addColumnIfMissing(connection, statement, "interview_video_session", "transcript_text", "TEXT");
        addColumnIfMissing(connection, statement, "interview_video_session", "summary_text", "TEXT");
        addColumnIfMissing(connection, statement, "interview_video_session", "summary_status", "VARCHAR(32)");
        widenColumnIfNeeded(connection, statement, "interview_video_session", "summary_status", "VARCHAR(128)");
        addColumnIfMissing(connection, statement, "interview_video_session", "hr_offer_sdp", "TEXT");
        addColumnIfMissing(connection, statement, "interview_video_session", "interviewee_answer_sdp", "TEXT");
        addColumnIfMissing(connection, statement, "interview_video_session", "hr_ice_candidates", "TEXT");
        addColumnIfMissing(connection, statement, "interview_video_session", "interviewee_ice_candidates", "TEXT");
        addColumnIfMissing(connection, statement, "interview_video_session", "recording_file_name", "VARCHAR(255)");
        addColumnIfMissing(connection, statement, "interview_video_session", "process_stage_id", "INTEGER");
        addColumnIfMissing(connection, statement, "interview_video_session", "stage_scope_id", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, statement, "interview_video_session", "last_activity_at", dateTimeType());
        statement.executeUpdate("UPDATE interview_video_session SET stage_scope_id = COALESCE(process_stage_id, 0)");
        statement.executeUpdate("UPDATE interview_video_session SET last_activity_at = "
                + "COALESCE(last_activity_at, updated_at, start_time, created_at, CURRENT_TIMESTAMP)");
        enforceVideoSessionIntegrityConstraints(connection, statement);
        archiveAndRemoveDuplicateVideoSessions(connection, statement);
    }

    private void archiveAndRemoveDuplicateVideoSessions(Connection connection, Statement statement) throws SQLException {
        String dateTimeType = dateTimeType();
        execute(statement, "CREATE TABLE IF NOT EXISTS interview_video_session_duplicate_archive ("
                + "original_session_id INTEGER PRIMARY KEY, process_id INTEGER NOT NULL, process_stage_id INTEGER, "
                + "stage_scope_id INTEGER NOT NULL, video_serial_no VARCHAR(128), video_join_link VARCHAR(500), "
                + "approver_user_id INTEGER, approver_name VARCHAR(64), interviewee_join_time " + dateTimeType + ", "
                + "hr_join_time " + dateTimeType + ", start_time " + dateTimeType + ", end_time " + dateTimeType + ", "
                + "recording_end_requested_at " + dateTimeType + ", recording_path VARCHAR(500), "
                + "hr_recording_path VARCHAR(500), hr_recording_file_name VARCHAR(255), "
                + "interviewee_recording_path VARCHAR(500), interviewee_recording_file_name VARCHAR(255), "
                + "merged_recording_path VARCHAR(500), merged_recording_file_name VARCHAR(255), "
                + "audio_path VARCHAR(500), audio_file_name VARCHAR(255), transcript_text TEXT, summary_text TEXT, "
                + "summary_status VARCHAR(128), hr_offer_sdp TEXT, interviewee_answer_sdp TEXT, "
                + "hr_ice_candidates TEXT, interviewee_ice_candidates TEXT, recording_file_name VARCHAR(255), "
                + "session_status VARCHAR(32), last_activity_at " + dateTimeType + ", created_at " + dateTimeType + ", "
                + "updated_at " + dateTimeType + ", archived_at " + dateTimeType + " NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        try (PreparedStatement cleanup = connection.prepareStatement(
                "DELETE FROM interview_video_session_duplicate_archive WHERE archived_at < ?")) {
            cleanup.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().minusDays(5)));
            cleanup.executeUpdate();
        }
        String duplicateQuality = videoSessionCompletenessExpression("duplicate_session");
        String betterQuality = videoSessionCompletenessExpression("better_session");
        String duplicatePredicate = "EXISTS (SELECT 1 FROM interview_video_session better_session "
                + "WHERE better_session.process_id = duplicate_session.process_id "
                + "AND better_session.stage_scope_id = duplicate_session.stage_scope_id AND ("
                + betterQuality + " > " + duplicateQuality + " OR (" + betterQuality + " = "
                + duplicateQuality + " AND better_session.id > duplicate_session.id)))";
        String archiveColumns = "original_session_id, process_id, process_stage_id, stage_scope_id, video_serial_no, "
                + "video_join_link, approver_user_id, approver_name, interviewee_join_time, hr_join_time, start_time, "
                + "end_time, recording_end_requested_at, recording_path, hr_recording_path, hr_recording_file_name, "
                + "interviewee_recording_path, interviewee_recording_file_name, merged_recording_path, "
                + "merged_recording_file_name, audio_path, audio_file_name, transcript_text, summary_text, "
                + "summary_status, hr_offer_sdp, interviewee_answer_sdp, hr_ice_candidates, "
                + "interviewee_ice_candidates, recording_file_name, session_status, last_activity_at, created_at, updated_at";
        String sourceColumns = archiveColumns.replace("original_session_id", "id");
        int archived = statement.executeUpdate("INSERT INTO interview_video_session_duplicate_archive "
                + "(" + archiveColumns + ") SELECT "
                + Arrays.stream(sourceColumns.split(", ")).map(column -> "duplicate_session." + column).collect(java.util.stream.Collectors.joining(", "))
                + " FROM interview_video_session duplicate_session WHERE " + duplicatePredicate + " "
                + "AND NOT EXISTS (SELECT 1 FROM interview_video_session_duplicate_archive archived "
                + "WHERE archived.original_session_id = duplicate_session.id)");
        int removed = statement.executeUpdate("DELETE FROM interview_video_session WHERE EXISTS ("
                + "SELECT 1 FROM interview_video_session_duplicate_archive archived "
                + "WHERE archived.original_session_id = interview_video_session.id "
                + "AND archived.process_id = interview_video_session.process_id "
                + "AND archived.stage_scope_id = interview_video_session.stage_scope_id)");
        if (archived > 0 || removed > 0) {
            log.warn("Archived {} and removed {} duplicate interview video session row(s) before enforcing uniqueness",
                    archived, removed);
        }
    }

    private String videoSessionCompletenessExpression(String alias) {
        return "(CASE WHEN " + alias + ".merged_recording_path IS NOT NULL AND " + alias
                + ".merged_recording_path <> '' THEN 128 ELSE 0 END + CASE WHEN " + alias
                + ".hr_recording_path IS NOT NULL AND " + alias + ".hr_recording_path <> '' THEN 32 ELSE 0 END + CASE WHEN "
                + alias + ".interviewee_recording_path IS NOT NULL AND " + alias
                + ".interviewee_recording_path <> '' THEN 32 ELSE 0 END + CASE WHEN " + alias
                + ".recording_path IS NOT NULL AND " + alias + ".recording_path <> '' THEN 16 ELSE 0 END + CASE WHEN "
                + alias + ".summary_text IS NOT NULL AND " + alias + ".summary_text <> '' THEN 8 ELSE 0 END + CASE WHEN "
                + alias + ".transcript_text IS NOT NULL AND " + alias + ".transcript_text <> '' THEN 4 ELSE 0 END + CASE "
                + alias + ".session_status WHEN 'PASSED' THEN 12 WHEN 'REJECTED' THEN 12 WHEN 'TERMINATED' THEN 12 "
                + "WHEN 'WAITING_APPROVAL' THEN 10 WHEN 'RECORDED' THEN 10 WHEN 'END_REQUESTED' THEN 8 "
                + "WHEN 'RECORDING' THEN 6 ELSE 0 END)";
    }

    private void enforceVideoSessionIntegrityConstraints(Connection connection, Statement statement) throws SQLException {
        if (activeDatabase.type() == DatabaseType.SQLITE) {
            return;
        }
        if (activeDatabase.type() == DatabaseType.PGSQL) {
            statement.executeUpdate("ALTER TABLE interview_video_session ALTER COLUMN stage_scope_id SET DEFAULT 0");
            statement.executeUpdate("ALTER TABLE interview_video_session ALTER COLUMN stage_scope_id SET NOT NULL");
            statement.executeUpdate("ALTER TABLE interview_video_session ALTER COLUMN last_activity_at SET DEFAULT CURRENT_TIMESTAMP");
            statement.executeUpdate("ALTER TABLE interview_video_session ALTER COLUMN last_activity_at SET NOT NULL");
        } else {
            statement.executeUpdate("ALTER TABLE interview_video_session MODIFY COLUMN stage_scope_id INTEGER NOT NULL DEFAULT 0");
            statement.executeUpdate("ALTER TABLE interview_video_session MODIFY COLUMN last_activity_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");
        }
        String constraintName = "ck_interview_video_session_stage_scope";
        if (!checkConstraintExists(connection, "interview_video_session", constraintName)) {
            statement.executeUpdate("ALTER TABLE interview_video_session ADD CONSTRAINT " + constraintName
                    + " CHECK (stage_scope_id = COALESCE(process_stage_id, 0))");
        }
    }

    private boolean checkConstraintExists(Connection connection, String table, String constraintName) throws SQLException {
        String sql = activeDatabase.type() == DatabaseType.PGSQL
                ? "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema=current_schema() "
                    + "AND table_name=? AND constraint_name=? AND constraint_type='CHECK'"
                : "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema=DATABASE() "
                    + "AND table_name=? AND constraint_name=? AND constraint_type='CHECK'";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, table);
            query.setString(2, constraintName);
            try (ResultSet result = query.executeQuery()) {
                return result.next() && result.getLong(1) > 0;
            }
        }
    }

    private String dateTimeType() {
        return activeDatabase.type() == DatabaseType.PGSQL ? "TIMESTAMP" : "DATETIME";
    }

    private void migrateRecruitmentCandidateColumns(Connection connection, Statement statement) throws SQLException {
        addColumnIfMissing(connection, statement, "recruitment_candidate", "resume_llm_score", "INTEGER");
        addColumnIfMissing(connection, statement, "recruitment_candidate", "resume_llm_comment", "VARCHAR(2000)");
        addColumnIfMissing(connection, statement, "recruitment_candidate", "resume_llm_status", "VARCHAR(32)");
        addColumnIfMissing(connection, statement, "recruitment_candidate", "resume_llm_evaluated_at", dateTimeType());
    }

    private void migrateRecruitmentJobColumns(Connection connection, Statement statement) throws SQLException {
        addColumnIfMissing(connection, statement, "recruitment_job", "department_id", "INTEGER");
        addColumnIfMissing(connection, statement, "recruitment_job", "default_overtime_rate", "DECIMAL(12,2) NOT NULL DEFAULT 0");
    }

    private void migrateHrEmployeeColumns(Connection connection, Statement statement) throws SQLException {
        addColumnIfMissing(connection, statement, "hr_employee", "source_candidate_id", "INTEGER");
        addColumnIfMissing(connection, statement, "hr_employee", "interview_stage_status", "VARCHAR(64)");
        addColumnIfMissing(connection, statement, "hr_employee", "source_channel", "VARCHAR(64)");
        addColumnIfMissing(connection, statement, "hr_employee", "notes", "VARCHAR(1000)");
        addColumnIfMissing(connection, statement, "hr_employee", "job_id", "INTEGER");
        addColumnIfMissing(connection, statement, "hr_employee", "base_salary", "DECIMAL(12,2) NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, statement, "hr_employee", "salary_confirmed", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, statement, "hr_employee", "overtime_rate", "DECIMAL(12,2)");
        addColumnIfMissing(connection, statement, "hr_employee", "dismissal_reason", "VARCHAR(64)");
        addColumnIfMissing(connection, statement, "hr_employee", "dismissal_date", "DATE");
        statement.executeUpdate("UPDATE hr_employee SET job_id = (SELECT MIN(j.id) FROM recruitment_job j "
                + "WHERE j.job_title = hr_employee.position_name) WHERE job_id IS NULL");
    }

    private void migrateInterviewLlmConfigColumns(Connection connection, Statement statement) throws SQLException {
        widenColumnIfNeeded(connection, statement, "interview_llm_config", "api_key", "VARCHAR(512)");
    }

    private void migratePayrollNumericColumns(Connection connection, Statement statement) throws SQLException {
        for (String column : new String[]{"cumulative_income", "cumulative_deduction_base",
                "cumulative_social_insurance", "cumulative_special_deduction", "cumulative_taxable_income",
                "cumulative_tax_withheld"}) {
            widenNumericColumnIfNeeded(connection, statement, "hr_payroll_month", column, 16, 2);
        }
    }

    private void migrateReferentialIntegrityConstraints(Connection connection, Statement statement) throws SQLException {
        if (activeDatabase.type() == DatabaseType.SQLITE) {
            return;
        }
        ensureForeignKey(connection, statement, "fk_candidate_interviewee_user", "recruitment_candidate", "interviewee_user_id", "sys_user");
        ensureForeignKey(connection, statement, "fk_process_candidate", "interview_process", "recruitment_candidate_id", "recruitment_candidate");
        ensureForeignKey(connection, statement, "fk_process_interviewee_user", "interview_process", "interviewee_user_id", "sys_user");
        ensureForeignKey(connection, statement, "fk_process_approver_user", "interview_process", "approved_hr_user_id", "sys_user");
        ensureForeignKey(connection, statement, "fk_process_template", "interview_process", "template_id", "interview_process_template");
        ensureForeignKey(connection, statement, "fk_process_stage_process", "interview_process_stage", "process_id", "interview_process");
        ensureForeignKey(connection, statement, "fk_process_stage_approver", "interview_process_stage", "approved_hr_user_id", "sys_user");
        ensureForeignKey(connection, statement, "fk_ai_record_process", "interview_ai_record", "process_id", "interview_process");
        ensureForeignKey(connection, statement, "fk_ai_record_stage", "interview_ai_record", "process_stage_id", "interview_process_stage");
        ensureForeignKey(connection, statement, "fk_video_session_process", "interview_video_session", "process_id", "interview_process");
        ensureForeignKey(connection, statement, "fk_video_session_stage", "interview_video_session", "process_stage_id", "interview_process_stage");
        ensureForeignKey(connection, statement, "fk_video_session_approver", "interview_video_session", "approver_user_id", "sys_user");
    }

    private void rebuildSqliteEmployeeTableForJobForeignKey(Connection connection, Statement statement) throws SQLException {
        if (foreignKeyExists(connection, "hr_employee", "job_id", "recruitment_job")) {
            return;
        }
        boolean foreignKeysEnabled;
        try (ResultSet result = statement.executeQuery("PRAGMA foreign_keys")) {
            foreignKeysEnabled = result.next() && result.getInt(1) == 1;
        }
        boolean originalAutoCommit = connection.getAutoCommit();
        if (!originalAutoCommit) {
            connection.commit();
        }
        statement.execute("PRAGMA foreign_keys=OFF");
        connection.setAutoCommit(false);
        try (Statement migration = connection.createStatement()) {
            migration.executeUpdate("CREATE TABLE hr_employee_migration ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, employee_code VARCHAR(64) NOT NULL UNIQUE, "
                    + "full_name VARCHAR(64) NOT NULL, id_card_no VARCHAR(32) NOT NULL UNIQUE, "
                    + "mobile_phone VARCHAR(32) NOT NULL UNIQUE, email VARCHAR(128), "
                    + "recruitment_major VARCHAR(128) NOT NULL, position_name VARCHAR(128) NOT NULL, "
                    + "manager_employee_id INTEGER, department_id INTEGER NOT NULL, "
                    + "bank_account_no VARCHAR(64) NOT NULL, bank_name VARCHAR(128) NOT NULL, "
                    + "hire_date DATE NOT NULL, employment_status INTEGER NOT NULL DEFAULT 0, "
                    + "source_candidate_id INTEGER, interview_stage_status VARCHAR(64), source_channel VARCHAR(64), "
                    + "notes VARCHAR(1000), job_id INTEGER, base_salary DECIMAL(12,2) NOT NULL DEFAULT 0, "
                    + "salary_confirmed INTEGER NOT NULL DEFAULT 0, overtime_rate DECIMAL(12,2), "
                    + "dismissal_reason VARCHAR(64), dismissal_date DATE, "
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (department_id) REFERENCES hr_department(id), "
                    + "FOREIGN KEY (job_id) REFERENCES recruitment_job(id))");
            String columns = "id,employee_code,full_name,id_card_no,mobile_phone,email,recruitment_major,"
                    + "position_name,manager_employee_id,department_id,bank_account_no,bank_name,hire_date,"
                    + "employment_status,source_candidate_id,interview_stage_status,source_channel,notes,job_id,"
                    + "base_salary,salary_confirmed,overtime_rate,dismissal_reason,dismissal_date,created_at,updated_at";
            migration.executeUpdate("INSERT INTO hr_employee_migration (" + columns + ") SELECT " + columns + " FROM hr_employee");
            migration.executeUpdate("DROP TABLE hr_employee");
            migration.executeUpdate("ALTER TABLE hr_employee_migration RENAME TO hr_employee");
            connection.commit();
            log.info("Rebuilt SQLite hr_employee table with job_id foreign key");
        } catch (SQLException | RuntimeException ex) {
            try {
                connection.rollback();
            } catch (SQLException rollbackError) {
                ex.addSuppressed(rollbackError);
            }
            throw ex;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
            if (foreignKeysEnabled) {
                statement.execute("PRAGMA foreign_keys=ON");
            }
        }
    }

    /**
     * The legacy SQLite data set contains a nullable employee source reference which was allowed to
     * outlive its candidate. This is the only historical relationship we repair automatically.
     */
    private void ensureEmployeeSourceCandidateForeignKey(Connection connection, Statement statement) throws SQLException {
        if (activeDatabase.type() != DatabaseType.PGSQL) {
            ensureForeignKey(connection, statement, "fk_hr_employee_source_candidate", "hr_employee",
                    "source_candidate_id", "recruitment_candidate");
            return;
        }

        boolean originalAutoCommit = connection.getAutoCommit();
        int originalIsolation = connection.getTransactionIsolation();
        try {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            try (Statement transactionStatement = connection.createStatement()) {
                transactionStatement.executeUpdate("CREATE TABLE IF NOT EXISTS database_migration_orphan_archive ("
                        + "table_name VARCHAR(64) NOT NULL, column_name VARCHAR(64) NOT NULL, "
                        + "child_id INTEGER NOT NULL, invalid_reference_id INTEGER NOT NULL, "
                        + "archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, row_data JSONB)");
                transactionStatement.executeUpdate("ALTER TABLE database_migration_orphan_archive "
                        + "ADD COLUMN IF NOT EXISTS row_data JSONB");
                transactionStatement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_migration_orphan_archive_expiry "
                        + "ON database_migration_orphan_archive (archived_at)");
                transactionStatement.executeUpdate("DELETE FROM database_migration_orphan_archive "
                        + "WHERE archived_at < CURRENT_TIMESTAMP - INTERVAL '5 days'");

                if (!foreignKeyExists(connection, "hr_employee", "source_candidate_id", "recruitment_candidate")) {
                    transactionStatement.executeUpdate("LOCK TABLE hr_employee, recruitment_candidate "
                            + "IN SHARE ROW EXCLUSIVE MODE");
                    int archivedRows = transactionStatement.executeUpdate("INSERT INTO database_migration_orphan_archive "
                            + "(table_name, column_name, child_id, invalid_reference_id, archived_at) "
                            + "SELECT 'hr_employee', 'source_candidate_id', child.id, child.source_candidate_id, CURRENT_TIMESTAMP "
                            + "FROM hr_employee child LEFT JOIN recruitment_candidate parent "
                            + "ON child.source_candidate_id = parent.id "
                            + "WHERE child.source_candidate_id IS NOT NULL AND parent.id IS NULL");
                    int repairedRows = transactionStatement.executeUpdate("UPDATE hr_employee child SET source_candidate_id = NULL "
                            + "WHERE child.source_candidate_id IS NOT NULL AND NOT EXISTS "
                            + "(SELECT 1 FROM recruitment_candidate parent WHERE parent.id = child.source_candidate_id)");
                    if (archivedRows != repairedRows) {
                        throw new SQLException("Employee source-candidate repair changed while migration was running");
                    }
                    if (repairedRows > 0) {
                        log.warn("Archived and cleared {} orphaned hr_employee.source_candidate_id value(s)", repairedRows);
                    }
                    assertNoOrphanedReferences(transactionStatement, "hr_employee", "source_candidate_id", "recruitment_candidate");
                    execute(transactionStatement, "ALTER TABLE hr_employee ADD CONSTRAINT fk_hr_employee_source_candidate "
                            + "FOREIGN KEY (source_candidate_id) REFERENCES recruitment_candidate(id) ON DELETE RESTRICT");
                }
            }
            connection.commit();
        } catch (SQLException | RuntimeException ex) {
            rollbackEmployeeSourceCandidateMigration(connection, ex);
            throw ex;
        } finally {
            connection.setTransactionIsolation(originalIsolation);
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private void rollbackEmployeeSourceCandidateMigration(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }

    private void ensureForeignKey(Connection connection, Statement statement, String constraintName, String table,
                                  String column, String referencedTable) throws SQLException {
        if (foreignKeyExists(connection, table, column, referencedTable)) {
            return;
        }
        assertNoOrphanedReferences(statement, table, column, referencedTable);
        execute(statement, "ALTER TABLE " + table + " ADD CONSTRAINT " + constraintName
                + " FOREIGN KEY (" + column + ") REFERENCES " + referencedTable + "(id) ON DELETE RESTRICT");
    }

    private boolean foreignKeyExists(Connection connection, String table, String column, String referencedTable) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet keys = metaData.getImportedKeys(null, null, table)) {
            while (keys.next()) {
                if (column.equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))
                        && referencedTable.equalsIgnoreCase(keys.getString("PKTABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void assertNoOrphanedReferences(Statement statement, String table, String column, String referencedTable)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + table + " child LEFT JOIN " + referencedTable
                + " parent ON child." + column + " = parent.id WHERE child." + column + " IS NOT NULL AND parent.id IS NULL";
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next() && resultSet.getLong(1) > 0) {
                throw new IllegalStateException("Cannot enforce foreign key " + table + "." + column
                        + " because orphaned references exist. Resolve them before deployment.");
            }
        }
    }

    private void addColumnIfMissing(Connection connection, Statement statement, String table, String column, String definition) throws SQLException {
        if (columnExists(connection, table, column)) {
            return;
        }
        execute(statement, "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        log.info("Added migration column {}.{}", table, column);
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null, table, column)) {
            if (columns.next()) {
                return true;
            }
        }
        try (ResultSet columns = metaData.getColumns(null, null, table.toUpperCase(), column.toUpperCase())) {
            return columns.next();
        }
    }

    private void migrateSysUserColumns(Connection connection, Statement statement) throws SQLException {
        addColumnIfMissing(connection, statement, "sys_user", "must_change_password", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, statement, "sys_user", "mobile_phone_normalized", "VARCHAR(32)");
        addColumnIfMissing(connection, statement, "sys_user", "email_normalized", "VARCHAR(128)");
        statement.executeUpdate("UPDATE sys_user SET mobile_phone_normalized = "
                + "CASE WHEN mobile_phone IS NULL OR TRIM(mobile_phone) = '' THEN NULL ELSE TRIM(mobile_phone) END");
        statement.executeUpdate("UPDATE sys_user SET email_normalized = "
                + "CASE WHEN email IS NULL OR TRIM(email) = '' THEN NULL ELSE LOWER(TRIM(email)) END");
        assertNoDuplicateNormalizedContacts(statement, "mobile_phone_normalized", "mobile phone");
        assertNoDuplicateNormalizedContacts(statement, "email_normalized", "email");
    }

    private void assertNoDuplicateNormalizedContacts(Statement statement, String column, String label) throws SQLException {
        String sql = "SELECT COUNT(*) FROM (SELECT " + column + " FROM sys_user WHERE " + column
                + " IS NOT NULL GROUP BY " + column + " HAVING COUNT(*) > 1) duplicate_contacts";
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next() && resultSet.getLong(1) > 0) {
                throw new IllegalStateException("Cannot add global " + label
                        + " uniqueness while duplicate normalized sys_user contacts exist. Resolve duplicates before deployment.");
            }
        }
    }

    private void assertNoDuplicateBusinessKeys(Statement statement) throws SQLException {
        assertNoDuplicateBusinessKey(statement, "recruitment_candidate", "job_id, interviewee_user_id",
                "interviewee_user_id IS NOT NULL", "candidate applications for one job and interviewee");
        assertNoDuplicateBusinessKey(statement, "interview_process", "recruitment_candidate_id",
                "recruitment_candidate_id IS NOT NULL", "interview processes for one candidate");
        assertNoDuplicateBusinessKey(statement, "interview_ai_record", "process_id, stage_scope_id, sequence_no",
                "sequence_no IS NOT NULL", "AI questions with one process stage and sequence number");
    }

    private void assertNoDuplicateBusinessKey(Statement statement, String table, String columns,
                                              String predicate, String label) throws SQLException {
        String sql = "SELECT COUNT(*) FROM (SELECT " + columns + " FROM " + table
                + " WHERE " + predicate + " GROUP BY " + columns + " HAVING COUNT(*) > 1) duplicate_rows";
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next() && resultSet.getLong(1) > 0) {
                throw new IllegalStateException("Cannot enforce uniqueness for " + label
                        + " while duplicate rows exist. Resolve duplicates before deployment.");
            }
        }
    }

    private void migrateInterviewProcessStageColumns(Connection connection, Statement statement) throws SQLException {
        addColumnIfMissing(connection, statement, "interview_process_stage", "round_knowledge_points", "VARCHAR(2000)");
        addColumnIfMissing(connection, statement, "interview_process_stage", "ai_recording_path", "VARCHAR(500)");
        addColumnIfMissing(connection, statement, "interview_process_stage", "ai_recording_file_name", "VARCHAR(255)");
    }

    private void migrateDatabaseGeneratedPrimaryKeys(Connection connection, Statement statement) throws SQLException {
        switch (activeDatabase.type()) {
            case SQLITE -> {
                // INTEGER PRIMARY KEY is already SQLite's row-id backed generated key.
            }
            case MYSQL -> migrateMySqlPrimaryKeys(connection, statement);
            case PGSQL -> migratePostgreSqlPrimaryKeys(connection, statement);
        }
    }

    private void migrateMySqlPrimaryKeys(Connection connection, Statement statement) throws SQLException {
        for (String table : PRIMARY_KEY_TABLES) {
            if (!isAutoIncrement(connection, table)) {
                statement.executeUpdate("ALTER TABLE " + table + " MODIFY COLUMN id INTEGER NOT NULL AUTO_INCREMENT");
                log.info("Enabled AUTO_INCREMENT for {}.id", table);
            }
            statement.executeUpdate("ALTER TABLE " + table + " AUTO_INCREMENT = " + nextPrimaryKeyValue(connection, table));
        }
    }

    private void migratePostgreSqlPrimaryKeys(Connection connection, Statement statement) throws SQLException {
        for (String table : PRIMARY_KEY_TABLES) {
            if (!hasPostgreSqlPrimaryKeyGenerator(connection, table)) {
                statement.executeUpdate("ALTER TABLE " + table + " ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY");
                log.info("Enabled identity generation for {}.id", table);
            }
            alignPostgreSqlSequence(connection, table);
        }
    }

    private boolean isAutoIncrement(Connection connection, String table) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null, table, "id")) {
            return columns.next() && "YES".equalsIgnoreCase(columns.getString("IS_AUTOINCREMENT"));
        }
    }

    private boolean hasPostgreSqlPrimaryKeyGenerator(Connection connection, String table) throws SQLException {
        String sql = "SELECT is_identity, column_default FROM information_schema.columns "
                + "WHERE table_schema = current_schema() AND table_name = ? AND column_name = 'id'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Missing primary key column " + table + ".id");
                }
                String defaultValue = resultSet.getString("column_default");
                return "YES".equalsIgnoreCase(resultSet.getString("is_identity"))
                        || (defaultValue != null && defaultValue.toLowerCase().startsWith("nextval("));
            }
        }
    }

    private void alignPostgreSqlSequence(Connection connection, String table) throws SQLException {
        String sequenceName;
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_get_serial_sequence(?, 'id')")) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getString(1) == null) {
                    throw new SQLException("Missing generated-key sequence for " + table + ".id");
                }
                sequenceName = resultSet.getString(1);
            }
        }
        long maximumId = maximumPrimaryKeyValue(connection, table);
        try (PreparedStatement statement = connection.prepareStatement("SELECT setval(CAST(? AS regclass), ?, ?)")) {
            statement.setString(1, sequenceName);
            statement.setLong(2, Math.max(maximumId, 1));
            statement.setBoolean(3, maximumId > 0);
            statement.execute();
        }
    }

    private long nextPrimaryKeyValue(Connection connection, String table) throws SQLException {
        long maximumId = maximumPrimaryKeyValue(connection, table);
        return maximumId == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(maximumId + 1, 1);
    }

    private long maximumPrimaryKeyValue(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT COALESCE(MAX(id), 0) FROM " + table)) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
        }
    }

    private void widenColumnIfNeeded(Connection connection, Statement statement, String table, String column,
                                     String definition) throws SQLException {
        if (activeDatabase.type() == DatabaseType.SQLITE) {
            return;
        }
        int open = definition.indexOf('(');
        int close = definition.indexOf(')', open + 1);
        if (open < 0 || close < 0) {
            throw new SQLException("Unsupported variable-length column definition: " + definition);
        }
        int desiredLength = Integer.parseInt(definition.substring(open + 1, close));
        String query = activeDatabase.type() == DatabaseType.PGSQL
                ? "SELECT data_type, character_maximum_length FROM information_schema.columns "
                    + "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?"
                : "SELECT data_type, character_maximum_length FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?";
        try (PreparedStatement queryStatement = connection.prepareStatement(query)) {
            queryStatement.setString(1, table);
            queryStatement.setString(2, column);
            try (ResultSet result = queryStatement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Missing column " + table + "." + column);
                }
                Number currentLength = (Number) result.getObject("character_maximum_length");
                String dataType = result.getString("data_type");
                boolean variableCharacter = "character varying".equalsIgnoreCase(dataType)
                        || "varchar".equalsIgnoreCase(dataType);
                if (!variableCharacter
                        || (currentLength != null && currentLength.longValue() >= desiredLength)) {
                    return;
                }
            }
        }
        if (activeDatabase.type() == DatabaseType.PGSQL) {
            statement.executeUpdate("ALTER TABLE " + table + " ALTER COLUMN " + column + " TYPE " + definition);
        } else {
            statement.executeUpdate("ALTER TABLE " + table + " MODIFY COLUMN " + column + " " + definition);
        }
    }

    private void widenNumericColumnIfNeeded(Connection connection, Statement statement, String table,
                                            String column, int desiredPrecision, int desiredScale) throws SQLException {
        if (activeDatabase.type() == DatabaseType.SQLITE) {
            return;
        }
        String query = activeDatabase.type() == DatabaseType.PGSQL
                ? "SELECT numeric_precision, numeric_scale FROM information_schema.columns "
                    + "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?"
                : "SELECT numeric_precision, numeric_scale FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?";
        int precision;
        int scale;
        try (PreparedStatement queryStatement = connection.prepareStatement(query)) {
            queryStatement.setString(1, table);
            queryStatement.setString(2, column);
            try (ResultSet result = queryStatement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Missing column " + table + "." + column);
                }
                precision = result.getInt("numeric_precision");
                scale = result.getInt("numeric_scale");
            }
        }
        if (precision >= desiredPrecision && scale >= desiredScale) {
            return;
        }
        String definition = "DECIMAL(" + desiredPrecision + "," + desiredScale + ")";
        if (activeDatabase.type() == DatabaseType.PGSQL) {
            statement.executeUpdate("ALTER TABLE " + table + " ALTER COLUMN " + column + " TYPE " + definition);
        } else {
            statement.executeUpdate("ALTER TABLE " + table + " MODIFY COLUMN " + column + " " + definition + " NOT NULL");
        }
    }

    private void acquireMigrationLock(Connection connection) throws SQLException {
        if (activeDatabase.type() == DatabaseType.PGSQL) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_lock(?)")) {
                statement.setLong(1, POSTGRES_MIGRATION_LOCK_ID);
                statement.execute();
            }
        } else if (activeDatabase.type() == DatabaseType.MYSQL) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 60)")) {
                statement.setString(1, MYSQL_MIGRATION_LOCK_NAME);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) {
                        throw new SQLException("Timed out waiting for the database migration lock");
                    }
                }
            }
        }
    }

    private void releaseMigrationLock(Connection connection) {
        String sql = switch (activeDatabase.type()) {
            case PGSQL -> "SELECT pg_advisory_unlock(?)";
            case MYSQL -> "SELECT RELEASE_LOCK(?)";
            case SQLITE -> null;
        };
        if (sql == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (activeDatabase.type() == DatabaseType.PGSQL) {
                statement.setLong(1, POSTGRES_MIGRATION_LOCK_ID);
            } else {
                statement.setString(1, MYSQL_MIGRATION_LOCK_NAME);
            }
            statement.execute();
        } catch (SQLException ex) {
            log.warn("Could not release the database migration lock", ex);
        }
    }

    private boolean messageContains(SQLException ex, String value) {
        return ex.getMessage() != null && ex.getMessage().contains(value);
    }

    private String firstLine(String sql) {
        int index = sql.indexOf('\n');
        return index >= 0 ? sql.substring(0, index) : sql;
    }
}
