package com.autohr.modules.school.service;

import com.autohr.common.exception.BusinessException;
import com.autohr.config.database.ActiveDatabase;
import com.autohr.config.database.AppMigrationProperties;
import com.autohr.config.database.DatabaseMigrationRunner;
import com.autohr.config.database.DatabaseType;
import com.autohr.modules.auth.entity.SysUser;
import com.autohr.modules.auth.mapper.SysUserMapper;
import com.autohr.modules.auth.service.JwtService;
import com.autohr.modules.interview.dto.InterviewVO;
import com.autohr.modules.interview.dto.StartInterviewProcessRequest;
import com.autohr.modules.interview.service.InterviewService;
import com.autohr.modules.recruitment.entity.RecruitmentCandidate;
import com.autohr.modules.recruitment.mapper.RecruitmentCandidateMapper;
import com.autohr.modules.recruitment.mapper.RecruitmentJobMapper;
import com.autohr.modules.school.dto.StudentRegistrationRequest;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchoolExamServiceTest {

    @TempDir
    Path tempDirectory;

    private JdbcTemplate jdbc;
    private SysUserMapper userMapper;
    private JwtService jwtService;
    private RecruitmentCandidateMapper candidateMapper;
    private InterviewService interviewService;
    private SchoolExamService service;

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:sqlite:" + tempDirectory.resolve("school-exam.db").toAbsolutePath().toString().replace('\\', '/');
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        new DatabaseMigrationRunner(dataSource, new ActiveDatabase(DatabaseType.SQLITE, url, "", "", false),
                new AppMigrationProperties()).run();
        jdbc = new JdbcTemplate(dataSource);
        userMapper = mock(SysUserMapper.class);
        jwtService = mock(JwtService.class);
        candidateMapper = mock(RecruitmentCandidateMapper.class);
        interviewService = mock(InterviewService.class);
        service = new SchoolExamService(
                jdbc,
                userMapper,
                mock(RecruitmentJobMapper.class),
                candidateMapper,
                interviewService,
                new BCryptPasswordEncoder(),
                jwtService);
    }

    @Test
    void importsClassesAndKeepsValidRowsWhenAnotherRowIsInvalid() throws Exception {
        Map<String, Object> result = service.importClasses(excelFile("classes.xlsx", workbook -> {
            workbook.createSheet("classes");
            workbook.getSheetAt(0).createRow(0).createCell(0).setCellValue("Major");
            workbook.getSheetAt(0).getRow(0).createCell(1).setCellValue("Class");
            workbook.getSheetAt(0).getRow(0).createCell(2).setCellValue("Code");
            workbook.getSheetAt(0).createRow(1).createCell(0).setCellValue("Computer Science");
            workbook.getSheetAt(0).getRow(1).createCell(1).setCellValue("Class 1");
            workbook.getSheetAt(0).getRow(1).createCell(2).setCellValue("CS-1");
            workbook.getSheetAt(0).createRow(2).createCell(0).setCellValue("Software Engineering");
            workbook.getSheetAt(0).getRow(2).createCell(1).setCellValue("Class 2");
            workbook.getSheetAt(0).getRow(2).createCell(2).setCellValue("CS-1");
        }));

        assertEquals(1, result.get("successCount"));
        assertEquals(1, result.get("failureCount"));
        List<Map<String, Object>> rows = rows(result.get("rows"));
        assertTrue((Boolean) rows.get(0).get("success"));
        assertFalse((Boolean) rows.get(1).get("success"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM school_class", Integer.class));
        assertEquals("CS-1", jdbc.queryForObject("SELECT class_code FROM school_class", String.class));
    }

    @Test
    void importsStudentsForKnownClassesAndReportsUnknownClassRows() throws Exception {
        jdbc.update("INSERT INTO school_class(major_name,class_name,class_code,status) VALUES(?,?,?,1)",
                "Computer Science", "Class 1", "CS-1");

        Map<String, Object> result = service.importStudents(excelFile("students.xlsx", workbook -> {
            workbook.createSheet("students");
            workbook.getSheetAt(0).createRow(0).createCell(0).setCellValue("Student number");
            workbook.getSheetAt(0).getRow(0).createCell(1).setCellValue("Name");
            workbook.getSheetAt(0).getRow(0).createCell(2).setCellValue("Class code");
            workbook.getSheetAt(0).createRow(1).createCell(0).setCellValue("2026001");
            workbook.getSheetAt(0).getRow(1).createCell(1).setCellValue("Ada");
            workbook.getSheetAt(0).getRow(1).createCell(2).setCellValue("CS-1");
            workbook.getSheetAt(0).createRow(2).createCell(0).setCellValue("2026002");
            workbook.getSheetAt(0).getRow(2).createCell(1).setCellValue("Grace");
            workbook.getSheetAt(0).getRow(2).createCell(2).setCellValue("UNKNOWN");
        }));

        assertEquals(1, result.get("successCount"));
        assertEquals(1, result.get("failureCount"));
        List<Map<String, Object>> rows = rows(result.get("rows"));
        assertTrue((Boolean) rows.get(0).get("success"));
        assertFalse((Boolean) rows.get(1).get("success"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM school_student", Integer.class));
        assertEquals("Ada", jdbc.queryForObject("SELECT full_name FROM school_student WHERE student_no='2026001'", String.class));
    }

    @Test
    void rejectsNonXlsxImportsBeforeOpeningTheWorkbook() {
        MockMultipartFile file = new MockMultipartFile("file", "classes.csv", "text/csv", "major,class".getBytes());

        assertThrows(BusinessException.class, () -> service.importClasses(file));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM school_class", Integer.class));
    }

    @Test
    void deletesAnExamThatHasNotBeenStarted() {
        long examId = seedDeletableExam();
        jdbc.update("INSERT INTO interview_job_knowledge_weight(job_id,knowledge_base_id,weight) VALUES(?,?,?)", 61L, 1L, 100);

        service.deleteExam(examId);

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM school_exam WHERE id=?", Integer.class, examId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM recruitment_job WHERE id=61", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM interview_job_knowledge_weight WHERE job_id=61", Integer.class));
    }

    @Test
    void refusesToDeleteAnExamWithAnAttempt() {
        long examId = seedDeletableExam();
        jdbc.update("INSERT INTO sys_user(id,username,password,role_code,status) VALUES(?,?,?,?,1)",
                71L, "student_delete", "not-used", "INTERVIEWEE");
        jdbc.update("INSERT INTO school_class(id,major_name,class_name,class_code,status) VALUES(?,?,?,?,1)",
                71L, "Computer Science", "Class 71", "CS-71");
        jdbc.update("INSERT INTO school_student(id,student_no,full_name,class_id,user_id,status) VALUES(?,?,?,?,?,1)",
                71L, "DELETE-71", "Ada", 71L, 71L);
        jdbc.update("INSERT INTO recruitment_candidate(id,job_id,full_name,mobile_phone,major,application_status,interview_stage_status,interviewee_user_id) "
                        + "VALUES(?,?,?,?,?,?,?,?)", 71L, 61L, "Ada", "school-DELETE-71", "Computer Science", "EXAM_STARTED", "In progress", 71L);
        jdbc.update("INSERT INTO interview_process(id,recruitment_candidate_id,interviewee_user_id,job_id,current_stage,stage_status,overall_status,process_status_view) "
                        + "VALUES(?,?,?,?,?,?,?,?)", 71L, 71L, 71L, 61L, "AI", "IN_PROGRESS", "IN_PROGRESS", "答题中");
        jdbc.update("INSERT INTO school_exam_attempt(exam_id,student_id,process_id) VALUES(?,?,?)", examId, 71L, 71L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.deleteExam(examId));

        assertTrue(error.getMessage().contains("已有答题记录"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM school_exam WHERE id=?", Integer.class, examId));
    }

    @Test
    void registrationBindsAnIntervieweeAccountAndRejectsDifferentRosterDetails() {
        jdbc.update("INSERT INTO school_class(major_name,class_name,class_code,status) VALUES(?,?,?,1)",
                "Computer Science", "Class 1", "CS-1");
        jdbc.update("INSERT INTO school_student(student_no,full_name,class_id,status) VALUES(?,?,?,1)",
                "2026001", "Ada", 1L);
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(88L);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(jwtService.generateToken(any(SysUser.class))).thenReturn("student-token");

        StudentRegistrationRequest request = registration("2026001", "Ada", 1L);
        Map<String, Object> response = service.registerStudent(request);

        assertEquals("student-token", response.get("token"));
        assertEquals(88L, jdbc.queryForObject("SELECT user_id FROM school_student WHERE student_no='2026001'", Long.class));
        ArgumentCaptor<SysUser> user = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(user.capture());
        assertEquals("student_2026001", user.getValue().getUsername());
        assertEquals("INTERVIEWEE", user.getValue().getRoleCode());

        assertThrows(BusinessException.class, () -> service.registerStudent(registration("2026001", "Grace", 1L)));
    }

    @Test
    void registrationRejectsUnknownStudentsWithoutCreatingRosterRecords() {
        jdbc.update("INSERT INTO school_class(major_name,class_name,class_code,status) VALUES(?,?,?,1)",
                "Computer Science", "Class 1", "CS-1");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.registerStudent(registration("2026999", "Unknown", 1L)));

        assertTrue(error.getMessage().contains("未找到学生档案"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM school_student", Integer.class));
    }

    @Test
    void analysisGroupsScoresByKnowledgePointForTheOwningStudent() {
        seedCompletedAttempt();
        jdbc.update("INSERT INTO interview_ai_record(process_id,knowledge_point,question_content,question_status,answer_status,average_score,sequence_no) "
                        + "VALUES(?,?,?,?,?,?,?)", 41L, "Java", "Question 1", "READY", "COMPLETED", 80, 1);
        jdbc.update("INSERT INTO interview_ai_record(process_id,knowledge_point,question_content,question_status,answer_status,average_score,sequence_no) "
                        + "VALUES(?,?,?,?,?,?,?)", 41L, "SQL", "Question 2", "READY", "COMPLETED", 60, 2);

        Map<String, Object> analysis = service.studentAttemptAnalysis(41L, 88L);

        assertEquals(70, analysis.get("scoreRate"));
        assertEquals(30, analysis.get("lossRate"));
        assertEquals(2, analysis.get("answeredRounds"));
        List<Map<String, Object>> points = rows(analysis.get("knowledgePoints"));
        assertEquals("Java", points.get(0).get("knowledgePoint"));
        assertEquals(80, points.get(0).get("scoreRate"));
        assertEquals("SQL", points.get(1).get("knowledgePoint"));
        assertEquals(60, points.get(1).get("scoreRate"));
        assertTrue(((String) analysis.get("aiSummary")).contains("70%"));
    }

    @Test
    void listingStudentAttemptsCalculatesAnalysisWithoutUpdatingTheAttempt() {
        seedCompletedAttempt();
        jdbc.update("INSERT INTO interview_ai_record(process_id,knowledge_point,question_content,question_status,answer_status,average_score,sequence_no) "
                        + "VALUES(?,?,?,?,?,?,?)", 41L, "Java", "Question 1", "READY", "COMPLETED", 80, 1);

        List<Map<String, Object>> attempts = service.listStudentAttempts(88L);

        assertEquals(80, attempts.get(0).get("scoreRate"));
        assertNull(jdbc.queryForObject("SELECT score_rate FROM school_exam_attempt WHERE process_id=?", Integer.class, 41L));
        assertNull(jdbc.queryForObject("SELECT submitted_at FROM school_exam_attempt WHERE process_id=?", String.class, 41L));
    }

    @Test
    void classAnalyticsKeepsInProgressAttemptsVisibleWithoutUsingThemInScoreAggregates() {
        seedCompletedAttempt();
        jdbc.update("INSERT INTO interview_ai_record(process_id,knowledge_point,question_content,question_status,answer_status,average_score,sequence_no) "
                        + "VALUES(?,?,?,?,?,?,?)", 41L, "Java", "Question 1", "READY", "COMPLETED", 80, 1);
        jdbc.update("INSERT INTO sys_user(id,username,password,role_code,status) VALUES(?,?,?,?,1)",
                89L, "student_2026002", "not-used", "INTERVIEWEE");
        jdbc.update("INSERT INTO school_student(id,student_no,full_name,class_id,user_id,status) VALUES(?,?,?,?,?,1)",
                10L, "2026002", "Grace", 1L, 89L);
        jdbc.update("INSERT INTO recruitment_candidate(id,job_id,full_name,mobile_phone,major,application_status,interview_stage_status,interviewee_user_id) "
                        + "VALUES(?,?,?,?,?,?,?,?)", 22L, 11L, "Grace", "school-2026002", "Computer Science", "EXAM_STARTED", "In progress", 89L);
        jdbc.update("INSERT INTO interview_process(id,recruitment_candidate_id,interviewee_user_id,job_id,current_stage,stage_status,overall_status,process_status_view) "
                        + "VALUES(?,?,?,?,?,?,?,?)", 42L, 22L, 89L, 11L, "AI", "IN_PROGRESS", "IN_PROGRESS", "Answering");
        jdbc.update("INSERT INTO school_exam_attempt(exam_id,student_id,process_id) VALUES(?,?,?)", 31L, 10L, 42L);

        Map<String, Object> analytics = service.analytics(31L, 1L);

        assertEquals(2, analytics.get("studentCount"));
        assertEquals(1, analytics.get("completedStudentCount"));
        assertEquals(80, analytics.get("scoreRate"));
        assertEquals(20, analytics.get("lossRate"));
        assertEquals(2, rows(analytics.get("students")).size());
    }

    @Test
    void listsPublishedExamWhenLocalDateTimeValuesUseIsoTSeparator() {
        seedStartableExam(60);
        jdbc.update("UPDATE school_exam SET publish_start=?,publish_end=? WHERE id=?",
                LocalDateTime.now().minusMinutes(1).withNano(0).toString(),
                LocalDateTime.now().plusMinutes(1).withNano(0).toString(), 31L);

        List<Map<String, Object>> exams = service.listStudentExams(88L);

        assertEquals(1, exams.size());
        assertEquals(31L, ((Number) exams.get(0).get("id")).longValue());
    }

    @Test
    void adminAttemptDetailsIncludesQuestionsAnswersAndScores() {
        seedCompletedAttempt();
        jdbc.update("INSERT INTO interview_ai_record(process_id,knowledge_point,question_content,question_status,answer_content,answer_status,interviewer_score,scorer_score,average_score,interviewer_comment,sequence_no) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?)", 41L, "Java", "Question 1", "READY", "My answer", "COMPLETED", 82, 78, 80, "Good explanation", 1);

        Map<String, Object> details = service.adminAttemptDetails(41L);

        assertEquals("Ada", details.get("fullName"));
        assertEquals(1L, details.get("answeredRounds"));
        List<Map<String, Object>> records = rows(details.get("records"));
        assertEquals(1, records.size());
        assertEquals("Question 1", records.get(0).get("questionContent"));
        assertEquals("My answer", records.get(0).get("answerContent"));
        assertEquals(80, records.get(0).get("averageScore"));
    }

    @Test
    void startsLowPassMarkExamWithAMatchingFollowUpThreshold() {
        seedStartableExam(40);
        doAnswer(invocation -> {
            RecruitmentCandidate candidate = invocation.getArgument(0);
            candidate.setId(21L);
            return 1;
        }).when(candidateMapper).insert(any(RecruitmentCandidate.class));
        InterviewVO started = new InterviewVO();
        started.setId(41L);
        when(interviewService.startInterviewProcess(any(StartInterviewProcessRequest.class))).thenReturn(started);

        Map<String, Object> result = service.startExam(31L, 88L);

        assertEquals(41L, result.get("processId"));
        ArgumentCaptor<StartInterviewProcessRequest> request = ArgumentCaptor.forClass(StartInterviewProcessRequest.class);
        verify(interviewService).startInterviewProcess(request.capture());
        assertEquals(40, request.getValue().getAiThresholdScore());
        assertEquals(40, request.getValue().getAiFollowUpThreshold());
        assertEquals(1, request.getValue().getAiMinQuestionRounds());
        assertEquals(1, request.getValue().getAiMaxQuestionRounds());
    }

    private void seedStartableExam(int passingScore) {
        jdbc.update("INSERT INTO sys_user(id,username,password,role_code,status) VALUES(?,?,?,?,1)",
                88L, "student_2026002", "not-used", "INTERVIEWEE");
        jdbc.update("INSERT INTO school_class(id,major_name,class_name,class_code,status) VALUES(?,?,?,?,1)",
                1L, "Computer Science", "Class 2", "CS-2");
        jdbc.update("INSERT INTO school_student(id,student_no,full_name,class_id,user_id,status) VALUES(?,?,?,?,?,1)",
                9L, "2026002", "Grace", 1L, 88L);
        jdbc.update("INSERT INTO recruitment_job(id,job_code,job_title,department_name,requirements,responsibilities,publish_date,status) "
                        + "VALUES(?,?,?,?,?,?,?,1)", 11L, "EX-2", "Exam", "Class 2", "", "", "2026-08-16");
        jdbc.update("INSERT INTO school_exam(id,exam_code,exam_name,class_id,legacy_job_id,question_rounds,passing_score,status) VALUES(?,?,?,?,?,?,?,?)",
                31L, "EX-2", "Low pass mark exam", 1L, 11L, 1, passingScore, "PUBLISHED");
    }

    private long seedDeletableExam() {
        jdbc.update("INSERT INTO recruitment_job(id,job_code,job_title,department_name,requirements,responsibilities,publish_date,status) "
                        + "VALUES(?,?,?,?,?,?,?,1)", 61L, "DELETE-EXAM", "Delete exam", "All students", "", "School exam", "2026-08-16");
        jdbc.update("INSERT INTO school_exam(id,exam_code,exam_name,legacy_job_id,question_rounds,passing_score,status) VALUES(?,?,?,?,?,?,?)",
                61L, "DELETE-EXAM", "Delete exam", 61L, 5, 60, "DRAFT");
        return 61L;
    }

    private void seedCompletedAttempt() {
        jdbc.update("INSERT INTO sys_user(id,username,password,role_code,status) VALUES(?,?,?,?,1)",
                88L, "student_2026001", "not-used", "INTERVIEWEE");
        jdbc.update("INSERT INTO school_class(id,major_name,class_name,class_code,status) VALUES(?,?,?,?,1)",
                1L, "Computer Science", "Class 1", "CS-1");
        jdbc.update("INSERT INTO school_student(id,student_no,full_name,class_id,user_id,status) VALUES(?,?,?,?,?,1)",
                9L, "2026001", "Ada", 1L, 88L);
        jdbc.update("INSERT INTO recruitment_job(id,job_code,job_title,department_name,requirements,responsibilities,publish_date,status) "
                        + "VALUES(?,?,?,?,?,?,?,1)", 11L, "EX-1", "Exam", "Class 1", "", "", "2026-08-16");
        jdbc.update("INSERT INTO recruitment_candidate(id,job_id,full_name,mobile_phone,major,application_status,interview_stage_status,interviewee_user_id) "
                        + "VALUES(?,?,?,?,?,?,?,?)", 21L, 11L, "Ada", "school-2026001", "Computer Science", "EXAM_STARTED", "In progress", 88L);
        jdbc.update("INSERT INTO interview_process(id,recruitment_candidate_id,interviewee_user_id,job_id,current_stage,stage_status,overall_status,process_status_view) "
                        + "VALUES(?,?,?,?,?,?,?,?)", 41L, 21L, 88L, 11L, "AI", "COMPLETED", "COMPLETED", "Completed");
        jdbc.update("INSERT INTO school_exam(id,exam_code,exam_name,class_id,legacy_job_id,status) VALUES(?,?,?,?,?,?)",
                31L, "EX-1", "Java Assessment", 1L, 11L, "PUBLISHED");
        jdbc.update("INSERT INTO school_exam_attempt(exam_id,student_id,process_id) VALUES(?,?,?)", 31L, 9L, 41L);
    }

    private StudentRegistrationRequest registration(String studentNo, String fullName, Long classId) {
        StudentRegistrationRequest request = new StudentRegistrationRequest();
        request.setStudentNo(studentNo);
        request.setFullName(fullName);
        request.setClassId(classId);
        return request;
    }

    private MockMultipartFile excelFile(String filename, WorkbookWriter writer) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writer.write(workbook);
            workbook.write(output);
            return new MockMultipartFile("file", filename,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @FunctionalInterface
    private interface WorkbookWriter {
        void write(XSSFWorkbook workbook);
    }
}
