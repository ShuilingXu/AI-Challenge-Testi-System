package com.autohr.modules.school.service;

import com.autohr.common.exception.BusinessException;
import com.autohr.modules.auth.service.AuthRedisSecurityStore;
import com.autohr.modules.interview.dto.AiAnswerRequest;
import com.autohr.modules.interview.service.InterviewService;
import com.autohr.modules.system.service.SystemConfigService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Real transactions and mappers, with a controllable external model. */
@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.config.import=", "interview.llm.allow-private-addresses=true",
        "school.llm.api-key=test-only", "school.llm.model=mock-model", "logging.level.com.autohr=INFO"})
class ExamWindowIntegrationTest {
    static final HttpServer llm;
    static final Path database;
    static volatile CountDownLatch evaluationStarted;
    static volatile CountDownLatch evaluationReleased;
    static volatile int modelScore = 85;
    static {
        try {
            database = Files.createTempDirectory("exam-window-").resolve("exam.db");
            llm = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            llm.createContext("/v1/chat/completions", exchange -> {
                try {
                    exchange.getRequestBody().readAllBytes();
                    evaluationStarted.countDown();
                    if (!evaluationReleased.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Test model timed out");
                    var body = ("{\"choices\":[{\"message\":{\"content\":\"{\\\"score\\\":" + modelScore + ",\\\"comment\\\":\\\"已说明本题核心概念并提供相关论证，适用条件仍有进一步完善空间。\\\",\\\"nextQuestion\\\":\\\"继续说明\\\"}\"}}]}").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                finally { exchange.close(); }
            });
            llm.start();
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.database.url", () -> "jdbc:sqlite:" + database);
        registry.add("school.llm.base-url", () -> "http://127.0.0.1:" + llm.getAddress().getPort() + "/v1");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired InterviewService interview;
    @Autowired SchoolExamService school;
    @MockBean SystemConfigService config;
    @MockBean AuthRedisSecurityStore redis;

    @BeforeEach void seed() {
        evaluationStarted = new CountDownLatch(1);
        evaluationReleased = new CountDownLatch(1);
        modelScore = 85;
        for (String table : List.of("school_answer_record", "school_exam_process_stage", "school_exam_attempt", "school_exam_process", "school_exam_template", "school_exam_candidate", "school_exam", "school_assessment_config", "school_student", "school_class"))
            jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO school_class(id,major_name,class_name,class_code) VALUES(1,'CS','Class','CS1')");
        jdbc.update("INSERT INTO school_class(id,major_name,class_name,class_code) VALUES(2,'CS','Other','CS2')");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=88", Integer.class) == 0)
            jdbc.update("INSERT INTO sys_user(id,username,password,role_code) VALUES(88,'window-student','test-only','STUDENT')");
        jdbc.update("INSERT INTO school_student(id,student_no,full_name,class_id,user_id) VALUES(9,'S9','Student',1,88)");
        jdbc.update("INSERT INTO school_assessment_config(id,job_code,job_title,department_name,requirements,responsibilities,publish_date) VALUES(11,'EX1','Exam','Class','知识','学校考试 AI 答题','2026-09-30')");
        jdbc.update("INSERT INTO school_exam(id,exam_code,exam_name,class_id,legacy_job_id,status) VALUES(31,'EX1','Exam',1,11,'PUBLISHED')");
        jdbc.update("INSERT INTO school_exam_candidate(id,job_id,full_name,mobile_phone,major,interviewee_user_id) VALUES(21,11,'Student','school-S9','CS',88)");
        jdbc.update("INSERT INTO school_exam_process(id,recruitment_candidate_id,interviewee_user_id,job_id,current_stage,stage_status,overall_status,process_status_view,ai_output_mode,ai_min_question_rounds,ai_max_question_rounds,ai_threshold_score,ai_follow_up_threshold) VALUES(41,21,88,11,'AI','IN_PROGRESS','IN_PROGRESS','答题中','SCHOOL_EXAM',1,1,60,60)");
        jdbc.update("INSERT INTO school_exam_attempt(exam_id,student_id,process_id) VALUES(31,9,41)");
        jdbc.update("INSERT INTO school_answer_record(id,process_id,question_content,question_status,answer_status,sequence_no) VALUES(51,41,'解释原子性','READY','PENDING',1)");
    }
    @AfterAll static void stop() { llm.stop(0); }

    private AiAnswerRequest answer() {
        var request = new AiAnswerRequest(); request.setProcessId(41L); request.setQuestionId(51L); request.setAnswerContent("全部成功或全部回滚"); return request;
    }
    private void close(String change) {
        switch (change.replace("_TEMPLATE", "")) {
            case "CLOSED" -> jdbc.update("UPDATE school_exam SET status='CLOSED' WHERE id=31");
            case "END" -> jdbc.update("UPDATE school_exam SET publish_end=? WHERE id=31", LocalDateTime.now().minusSeconds(1).toString());
            case "START" -> jdbc.update("UPDATE school_exam SET publish_start=? WHERE id=31", LocalDateTime.now().plusHours(1).toString());
            case "CLASS" -> jdbc.update("UPDATE school_exam SET class_id=2 WHERE id=31");
        }
    }

    @ParameterizedTest @ValueSource(strings = {"CLOSED", "END", "START", "CLASS", "CLOSED_TEMPLATE", "END_TEMPLATE", "START_TEMPLATE", "CLASS_TEMPLATE"})
    void closedWindowBlocksResumeQuestionAndBothSubmissionEntrypoints(String change) {
        if (change.endsWith("_TEMPLATE")) seedTemplate();
        close(change);
        assertThrows(BusinessException.class, () -> school.startExam(31L, 88L));
        assertThrows(BusinessException.class, () -> interview.getIntervieweeNextAiQuestion(41L, 88L));
        assertThrows(BusinessException.class, () -> interview.submitIntervieweeAiAnswer(answer(), 88L));
        assertThrows(BusinessException.class, () -> interview.submitAiAnswer(answer()));
        assertNull(jdbc.queryForObject("SELECT answer_content FROM school_answer_record WHERE id=51", String.class));
        assertNull(jdbc.queryForObject("SELECT average_score FROM school_answer_record WHERE id=51", Integer.class));
        assertEquals(1L, evaluationStarted.getCount());
    }

    @ParameterizedTest @ValueSource(strings = {"CLOSED", "END", "CLOSED_TEMPLATE", "END_TEMPLATE"})
    void closingDuringModelEvaluationPreventsFormalScoreCommit(String change) throws Exception {
        if (change.endsWith("_TEMPLATE")) seedTemplate();
        var worker = Executors.newSingleThreadExecutor();
        try {
            var pending = worker.submit(() -> interview.submitIntervieweeAiAnswer(answer(), 88L));
            assertTrue(evaluationStarted.await(10, TimeUnit.SECONDS));
            close(change);
            evaluationReleased.countDown();
            var failure = assertThrows(ExecutionException.class, () -> pending.get(10, TimeUnit.SECONDS));
            assertInstanceOf(BusinessException.class, failure.getCause());
            assertNull(jdbc.queryForObject("SELECT average_score FROM school_answer_record WHERE id=51", Integer.class));
            assertNull(jdbc.queryForObject("SELECT ai_average_score FROM school_exam_process WHERE id=41", Integer.class));
            assertEquals("GRADE_NOT_COMMITTED", jdbc.queryForObject("SELECT answer_error FROM school_answer_record WHERE id=51", String.class));
            assertEquals("FAILED", jdbc.queryForObject("SELECT answer_status FROM school_answer_record WHERE id=51", String.class));
            assertEquals("IN_PROGRESS", jdbc.queryForObject("SELECT overall_status FROM school_exam_process WHERE id=41", String.class));
            assertEquals(answer().getAnswerContent(), jdbc.queryForObject("SELECT answer_content FROM school_answer_record WHERE id=51", String.class));
        } finally { evaluationReleased.countDown(); worker.shutdownNow(); }
    }

    private void seedTemplate() {
        jdbc.update("INSERT INTO school_exam_template(id,template_name,status) VALUES(71,'Template',1)");
        jdbc.update("UPDATE school_exam_process SET template_id=71 WHERE id=41");
        jdbc.update("INSERT INTO school_exam_process_stage(id,process_id,stage_name,stage_type,sequence_no,stage_status) VALUES(81,41,'AI stage','AI',1,'IN_PROGRESS')");
        jdbc.update("UPDATE school_answer_record SET process_stage_id=81,stage_scope_id=81 WHERE id=51");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void openWindowStillCommitsGradeAndCompletesExam(boolean template) {
        if (template) seedTemplate();
        evaluationReleased.countDown();
        assertEquals(85, interview.submitIntervieweeAiAnswer(answer(), 88L).getAverageScore());
        assertEquals(85, jdbc.queryForObject("SELECT average_score FROM school_answer_record WHERE id=51", Integer.class));
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT overall_status FROM school_exam_process WHERE id=41", String.class));
    }

    @ParameterizedTest @ValueSource(ints = {35, 85})
    void hiddenGradeHasNeutralResultAcrossSubmissionProcessAndRecords(int score) {
        modelScore = score;
        jdbc.update("UPDATE school_exam SET show_live_score=0,show_final_score=0 WHERE id=31");
        evaluationReleased.countDown();
        var submitted = interview.submitIntervieweeAiAnswer(answer(), 88L);
        for (var value : List.of(submitted, interview.getIntervieweeProcess(41L, 88L),
                interview.heartbeat(41L, 88L), interview.listIntervieweeAiRecords(41L, 88L).get(0))) {
            assertNull(value.getAverageScore()); assertNull(value.getInterviewerScore());
            assertNull(value.getScorerScore()); assertNull(value.getAiAverageScore());
            assertNull(value.getAiThresholdScore()); assertNull(value.getAiFollowUpThreshold());
            assertNull(value.getInterviewerComment());
            assertEquals("答题已提交", value.getProcessStatusView());
            assertEquals("COMPLETED", value.getOverallStatus());
            assertEquals("COMPLETED", value.getStageStatus());
        }
        assertEquals(score, jdbc.queryForObject("SELECT average_score FROM school_answer_record WHERE id=51", Integer.class));
        assertEquals(score < 60 ? "REJECTED" : "COMPLETED", jdbc.queryForObject("SELECT overall_status FROM school_exam_process WHERE id=41", String.class));
    }
}
