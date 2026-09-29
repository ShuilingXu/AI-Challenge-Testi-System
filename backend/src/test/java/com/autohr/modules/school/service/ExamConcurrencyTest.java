package com.autohr.modules.school.service;

import com.autohr.modules.school.dto.SchoolExamSaveRequest;
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
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    final Map<String, List<Long>> timings = new ConcurrentHashMap<>();

    @AfterAll static void stop() { llm.stop(0); mockWorkers.shutdownNow(); }

    @Test
    void sixtyStudentsCompleteTwoRoundsWithoutLostOrDuplicateScores() throws Exception {
        jdbc.update("INSERT INTO school_class(id,major_name,class_name,class_code) VALUES(900,'测试','并发班','LOAD60')");
        jdbc.update("INSERT INTO interview_knowledge_base(id,knowledge_base_name) VALUES(900,'事务')");
        for (int i = 0; i < 101; i++) {
            jdbc.update("INSERT INTO interview_knowledge_item(knowledge_base_id,knowledge_point,knowledge_content) VALUES(900,?,?)", "其他主题" + i, "无关材料" + i);
        }
        jdbc.update("INSERT INTO interview_knowledge_item(knowledge_base_id,knowledge_point,knowledge_content) VALUES(900,'事务','原子性要求事务全部完成或全部回滚。')");
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
                    String token = login.path("token").asText();
                    assertFalse(token.isBlank());
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
        assertEquals(120, jdbc.queryForObject("SELECT COUNT(*) FROM interview_ai_record WHERE answer_status='COMPLETED' AND average_score=85", Integer.class));
        // The current application reads final status/score from interview_process;
        // school_exam_attempt's legacy summary columns are not populated on submission.
        assertEquals(60, jdbc.queryForObject("SELECT COUNT(*) FROM school_exam_attempt a JOIN interview_process p ON p.id=a.process_id WHERE p.overall_status='COMPLETED' AND p.ai_average_score=85", Integer.class));
        assertEquals(60, jdbc.queryForObject("SELECT COUNT(DISTINCT student_id) FROM school_exam_attempt", Integer.class));
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

    JsonNode request(String stage, String path, String token, Map<String, ?> body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path))
                .timeout(Duration.ofSeconds(120)).header("Cookie", "AUTOHR_CSRF=load-test")
                .header("X-CSRF-Token", "load-test");
        if (!token.isBlank()) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8));
        long begun = System.nanoTime();
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        timings.computeIfAbsent(stage, ignored -> Collections.synchronizedList(new ArrayList<>())).add((System.nanoTime()-begun)/1_000_000);
        assertEquals(200, response.statusCode(), stage + ": " + response.body());
        JsonNode json = JSON.readTree(response.body());
        assertTrue(json.path("success").asBoolean(), stage + ": " + response.body());
        return json.path("data");
    }
}
