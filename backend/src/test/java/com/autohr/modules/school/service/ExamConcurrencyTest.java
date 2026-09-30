package com.autohr.modules.school.service;

import com.autohr.modules.school.dto.SchoolExamSaveRequest;
import com.autohr.modules.auth.service.AuthRedisSecurityStore;
import com.autohr.modules.system.service.SystemConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP + real SQLite, only the external LLM/config file are isolated. */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.import=", "logging.level.com.autohr=INFO",
                "interview.llm.allow-private-addresses=true", "school.llm.api-key=load-test-only",
                "school.llm.model=mock-model", "school.llm.default-prompt=学校考试助手",
                "school.llm.interviewer-prompt=", "school.llm.scorer-prompt="})
class ExamConcurrencyTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final AtomicInteger evaluations = new AtomicInteger();
    static final AtomicInteger questions = new AtomicInteger();
    static final AtomicInteger summaries = new AtomicInteger();
    static final AtomicInteger invalidContexts = new AtomicInteger();
    static final ExecutorService mockWorkers = Executors.newFixedThreadPool(64);
    static final HttpServer llm;
    static final Path database;
    static final int DELAY_MS = Integer.getInteger("exam.load.llmDelayMs", 250);
    static final boolean PREBOUND_ACCOUNTS = Boolean.getBoolean("exam.load.preboundAccounts");
    static {
        try {
            database = Files.createTempDirectory("exam-load-").resolve("exam.db");
            llm = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 128);
            llm.setExecutor(mockWorkers);
            llm.createContext("/v1/chat/completions", exchange -> {
                try {
                    JsonNode payload = JSON.readTree(exchange.getRequestBody());
                    String prompt = payload.path("messages").get(0).path("content").asText();
                    String content;
                    if (prompt.contains("JSON格式")) {
                        evaluations.incrementAndGet();
                        String context = payload.path("messages").get(1).path("content").asText();
                        if (!prompt.contains("根据你可靠的领域知识进行拓展评分")
                                || !context.contains("原子性") || context.contains("无关材料")) invalidContexts.incrementAndGet();
                        content = "{\"score\":85,\"comment\":\"已准确说明知识库中的核心概念，论证基本完整，但对适用条件的说明仍可更清晰。\",\"nextQuestion\":\"请解释适用条件。\"}";
                    } else if (prompt.contains("学习诊断")) {
                        summaries.incrementAndGet();
                        content = "事务核心概念掌握较好，建议结合逐题反馈复习适用条件。";
                    } else {
                        questions.incrementAndGet();
                        content = "请解释事务的原子性及其作用。";
                    }
                    Thread.sleep(DELAY_MS);
                    byte[] response = JSON.writeValueAsBytes(Map.of("choices", List.of(Map.of("message", Map.of("content", content)))));
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                } finally { exchange.close(); }
            });
            llm.start();
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.database.url", () -> "jdbc:sqlite:" + database);
        registry.add("school.llm.base-url", () -> "http://127.0.0.1:" + llm.getAddress().getPort() + "/v1");
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired SchoolExamService school;
    // Prevent runtime reload from reading the developer's actual model credentials.
    @MockBean SystemConfigService config;
    // Keep the load test independent of an external Redis instance. The real
    // rate-limit service still runs; focused tests cover its rejected requests.
    @MockBean AuthRedisSecurityStore securityStore;
    final ThreadLocal<String> registeredSession = new ThreadLocal<>();
    final Map<String, String> studentSessions = new ConcurrentHashMap<>();
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    final Map<String, List<Long>> timings = new ConcurrentHashMap<>();

    @AfterAll static void stop() { llm.stop(0); mockWorkers.shutdownNow(); }

    @Test
    void sixtyStudentsCompleteTwoRoundsWithoutLostOrDuplicateScores() throws Exception {
        jdbc.update("INSERT INTO school_class(id,major_name,class_name,class_code) VALUES(900,'测试','并发班','LOAD60')");
        jdbc.update("INSERT INTO school_knowledge_base(id,knowledge_base_name) VALUES(900,'事务')");
        for (int i = 0; i < 101; i++) {
            jdbc.update("INSERT INTO school_knowledge_item(knowledge_base_id,knowledge_point,knowledge_content) VALUES(900,?,?)", "其他主题" + i, "无关材料" + i);
        }
        jdbc.update("INSERT INTO school_knowledge_item(knowledge_base_id,knowledge_point,knowledge_content) VALUES(900,'事务','原子性要求事务全部完成或全部回滚。')");
        for (int i = 0; i < 60; i++) {
            if (PREBOUND_ACCOUNTS) {
                jdbc.update("INSERT INTO sys_user(id,username,password,role_code,display_name,status,must_change_password,token_version) VALUES(?,?,?,'STUDENT',?,1,0,0)",
                        1000 + i, "load" + i, "unused-test-password", "学生" + i);
            }
            jdbc.update("INSERT INTO school_student(student_no,full_name,class_id,user_id) VALUES(?,?,900,?)", "load" + i, "学生" + i, PREBOUND_ACCOUNTS ? 1000 + i : null);
        }
        SchoolExamSaveRequest exam = new SchoolExamSaveRequest();
        exam.setExamCode("LOAD60"); exam.setExamName("并发考试"); exam.setClassId(900L);
        exam.setKnowledgeBaseId(900L); exam.setQuestionRounds(2); exam.setFollowUpRounds(0);
        exam.setPassingScore(60); exam.setStatus("PUBLISHED");
        long examId = ((Number) school.saveExam(exam).get("id")).longValue();
        ExecutorService students = Executors.newFixedThreadPool(60);
        CyclicBarrier start = new CyclicBarrier(60);
        CyclicBarrier submit = new CyclicBarrier(60);
        long begun = System.nanoTime();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                final int id = i;
                futures.add(students.submit(() -> {
                    start.await(20, TimeUnit.SECONDS);
                    JsonNode login = request("registration", "/exams/student-registration", "", Map.of("classId", 900, "studentNo", "load" + id, "fullName", "学生" + id));
                    assertFalse(login.has("token"), "student entry must keep the JWT in the HttpOnly cookie");
                    String token = registeredSession.get();
                    assertFalse(token.isBlank());
                    studentSessions.put("load" + id, token);
                    long process = request("start", "/exams/student/exams/" + examId + "/start", token, Map.of()).path("processId").asLong();
                    assertTrue(process > 0);
                    for (int round = 0; round < 2; round++) {
                        JsonNode question = null;
                        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
                        while (System.nanoTime() < deadline) {
                            question = request("poll", "/interview/interviewee/next-question/" + process, token, null);
                            if ("READY".equals(question.path("questionStatus").asText())) break;
                            Thread.sleep(200);
                        }
                        assertNotNull(question);
                        assertEquals("READY", question.path("questionStatus").asText());
                        request("heartbeat", "/interview/interviewee/heartbeat/" + process, token, Map.of());
                        submit.await(90, TimeUnit.SECONDS);
                        Map<String, Object> answer = Map.of("processId", process, "questionId", question.path("id").asLong(), "answerContent", "事务必须全部完成或全部回滚，避免部分更新。学生" + id);
                        JsonNode scored = request("answer", "/interview/interviewee/ai-answer", token, answer);
                        assertEquals(85, scored.path("averageScore").asInt());
                        // A network retry must return the persisted result without another LLM call.
                        assertEquals(85, request("retry", "/interview/interviewee/ai-answer", token, answer).path("averageScore").asInt());
                    }
                    request("analysis", "/exams/student/attempts/" + process + "/analysis", token, null);
                    return null;
                }));
            }
            for (Future<?> future : futures) future.get(180, TimeUnit.SECONDS);
        } finally { students.shutdownNow(); }
        assertEquals(120, evaluations.get(), "one model evaluation per answer, none on retry");
        assertEquals(120, questions.get());
        assertEquals(60, summaries.get());
        assertEquals(0, invalidContexts.get(), "retrieve relevant knowledge even beyond the first 100 rows");
        assertEquals(120, jdbc.queryForObject("SELECT COUNT(*) FROM school_answer_record WHERE answer_status='COMPLETED' AND average_score=85", Integer.class));
        // The current application reads final status/score from school_exam_process;
        // school_exam_attempt's legacy summary columns are not populated on submission.
        assertEquals(60, jdbc.queryForObject("SELECT COUNT(*) FROM school_exam_attempt a JOIN school_exam_process p ON p.id=a.process_id WHERE p.overall_status='COMPLETED' AND p.ai_average_score=85", Integer.class));
        assertEquals(60, jdbc.queryForObject("SELECT COUNT(DISTINCT student_id) FROM school_exam_attempt", Integer.class));
        verifyStudentSecurity(examId);
        StringBuilder report = new StringBuilder("students=60 rounds=2 preboundAccounts=" + PREBOUND_ACCOUNTS + " mockLlmDelayMs=" + DELAY_MS + " elapsedMs=" + (System.nanoTime() - begun) / 1_000_000 + "\n");
        timings.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            List<Long> sorted = entry.getValue().stream().sorted().toList();
            report.append(entry.getKey()).append(" requests=").append(sorted.size()).append(" p50Ms=").append(sorted.get(sorted.size()/2))
                    .append(" p95Ms=").append(sorted.get((int)Math.ceil(sorted.size()*.95)-1)).append(" maxMs=").append(sorted.get(sorted.size()-1)).append('\n');
        });
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/exam-load-report.txt"), report);
        System.out.println(report);
    }

    private void verifyStudentSecurity(long examId) throws Exception {
        for (String path : List.of("/", "/login", "/admin/exams", "/exam/take/41")) {
            HttpResponse<String> page = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode(), path);
            assertTrue(page.body().contains("review-frontend-fixture"), path);
        }
        HttpResponse<String> asset = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/assets/review.js")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, asset.statusCode());
        assertTrue(asset.body().contains("reviewFrontendFixture"));
        String token = studentSessions.get("load0");
        long processId = jdbc.queryForObject("SELECT a.process_id FROM school_exam_attempt a JOIN school_student s ON s.id=a.student_id WHERE s.student_no='load0'", Long.class);
        long otherProcessId = jdbc.queryForObject("SELECT a.process_id FROM school_exam_attempt a JOIN school_student s ON s.id=a.student_id WHERE s.student_no='load1'", Long.class);
        assertEquals(400, rawRequest("/interview/interviewee/process/" + otherProcessId, token, null).statusCode());
        jdbc.update("UPDATE school_exam SET show_live_score=0,show_final_score=0 WHERE id=?", examId);
        JsonNode history = request("security-history", "/exams/student/attempts", token, null).get(0);
        for (String key : List.of("averageScore", "scoreRate", "lossRate", "aiSummary")) assertTrue(history.path(key).isNull(), key);
        assertTrue(request("security-process", "/interview/interviewee/process/" + processId, token, null).path("aiAverageScore").isNull());
        JsonNode records = request("security-records", "/interview/interviewee/ai-records?processId=" + processId, token, null).path("items");
        for (JsonNode record : records) {
            for (String key : List.of("averageScore", "interviewerScore", "scorerScore", "interviewerComment")) assertTrue(record.path(key).isNull(), key);
        }
        assertEquals(400, rawRequest("/exams/student/attempts/" + processId + "/analysis", token, null).statusCode());
        jdbc.update("UPDATE school_student SET status=0 WHERE student_no='load0'");
        assertEquals(400, rawRequest("/interview/interviewee/process/" + processId, token, null).statusCode());
        assertEquals(400, rawRequest("/interview/interviewee/heartbeat/" + processId, token, Map.of()).statusCode());
        assertEquals(400, rawRequest("/exams/student/attempts", token, null).statusCode());
        jdbc.update("UPDATE school_student SET status=1 WHERE student_no='load0'");
        jdbc.update("UPDATE school_class SET status=0 WHERE id=900");
        assertEquals(400, rawRequest("/interview/interviewee/process/" + processId, token, null).statusCode());
        jdbc.update("UPDATE school_class SET status=1 WHERE id=900");
        jdbc.update("UPDATE school_exam SET show_live_score=1,show_final_score=1 WHERE id=?", examId);
        assertEquals(85, request("security-restored", "/interview/interviewee/process/" + processId, token, null).path("aiAverageScore").asInt());
        // Simulate a worker disappearing after persisting the answer. An
        // expired lease must reappear for a retry with that exact saved answer.
        long recordId = jdbc.queryForObject("SELECT id FROM school_answer_record WHERE process_id=? ORDER BY sequence_no LIMIT 1", Long.class, processId);
        String savedAnswer = jdbc.queryForObject("SELECT answer_content FROM school_answer_record WHERE id=?", String.class, recordId);
        jdbc.update("UPDATE school_exam_process SET overall_status='IN_PROGRESS',stage_status='IN_PROGRESS' WHERE id=?", processId);
        jdbc.update("UPDATE school_answer_record SET answer_status='PROCESSING',average_score=NULL,answer_processing_token='expired-test',answer_lease_expires_at=? WHERE id=?",
                java.time.LocalDateTime.now().minusMinutes(1), recordId);
        JsonNode recovered = request("expired-answer", "/interview/interviewee/next-question/" + processId, token, null);
        assertEquals(recordId, recovered.path("id").asLong());
        assertEquals(savedAnswer, recovered.path("answerContent").asText());
        int beforeRetry = evaluations.get();
        JsonNode result = request("expired-retry", "/interview/interviewee/ai-answer", token,
                Map.of("processId", processId, "questionId", recordId, "answerContent", savedAnswer));
        assertEquals(85, result.path("averageScore").asInt());
        assertEquals(beforeRetry + 1, evaluations.get());
    }

    JsonNode request(String stage, String path, String token, Map<String, ?> body) throws Exception {
        long begun = System.nanoTime();
        var response = rawRequest(path, token, body);
        timings.computeIfAbsent(stage, ignored -> Collections.synchronizedList(new ArrayList<>())).add((System.nanoTime()-begun)/1_000_000);
        assertEquals(200, response.statusCode(), stage + ": " + response.body());
        JsonNode json = JSON.readTree(response.body());
        assertTrue(json.path("success").asBoolean(), stage + ": " + response.body());
        return json.path("data");
    }

    HttpResponse<String> rawRequest(String path, String token, Map<String, ?> body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path))
                .timeout(Duration.ofSeconds(120)).header("Cookie", "AUTOHR_CSRF=load-test" + (token.isBlank() ? "" : "; AUTOHR_SESSION=" + token))
                .header("X-CSRF-Token", "load-test");
        if (body != null) builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8));
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        for (String cookie : response.headers().allValues("Set-Cookie")) {
            if (cookie.startsWith("AUTOHR_SESSION=")) {
                assertTrue(cookie.contains("HttpOnly"));
                registeredSession.set(cookie.substring("AUTOHR_SESSION=".length()).split(";", 2)[0]);
            }
        }
        return response;
    }
}
