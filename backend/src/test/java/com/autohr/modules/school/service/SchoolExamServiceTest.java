package com.autohr.modules.school.service;

import com.autohr.common.exception.BusinessException;
import com.autohr.config.database.ActiveDatabase;
import com.autohr.config.database.AppMigrationProperties;
import com.autohr.config.database.DatabaseMigrationRunner;
import com.autohr.config.database.DatabaseType;
import com.autohr.modules.auth.entity.SysUser;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.service.AuditLogService;
import com.autohr.modules.auth.mapper.SysUserMapper;
import com.autohr.modules.auth.service.JwtService;
import com.autohr.modules.interview.dto.InterviewVO;
import com.autohr.modules.interview.dto.StartInterviewProcessRequest;
import com.autohr.modules.interview.service.InterviewService;
import com.autohr.modules.recruitment.entity.RecruitmentCandidate;
import com.autohr.modules.recruitment.entity.RecruitmentJob;
import com.autohr.modules.recruitment.mapper.RecruitmentCandidateMapper;
import com.autohr.modules.recruitment.mapper.RecruitmentJobMapper;
import com.autohr.modules.school.dto.SchoolClassSaveRequest;
import com.autohr.modules.school.dto.SchoolExamSaveRequest;
import com.autohr.modules.school.dto.StudentRegistrationRequest;
import com.autohr.modules.school.dto.ScoreReviewRequest;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

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
    private RecruitmentJobMapper jobMapper;
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
        jobMapper = mock(RecruitmentJobMapper.class);
        interviewService = mock(InterviewService.class);
        service = new SchoolExamService(
                jdbc,
                userMapper,
                jobMapper,
                candidateMapper,
                interviewService,
                new BCryptPasswordEncoder(),
                jwtService);
        ReflectionTestUtils.setField(service, "auditLogService", mock(AuditLogService.class));
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
    void generatesLegacyXlsTemplates() throws Exception {
        assertTrue(service.classesTemplate().length > 100);
        assertTrue(service.studentsTemplate().length > 100);
        try (HSSFWorkbook workbook = new HSSFWorkbook(new java.io.ByteArrayInputStream(service.classesTemplate()))) {
            assertEquals("专业", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
        }
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
    void savedClassAppearsInTheClassList() {
        SchoolClassSaveRequest request = new SchoolClassSaveRequest();
        request.setMajorName("Computer Science");
        request.setClassName("Class 1");
        request.setClassCode("CS-1");

        Map<String, Object> saved = service.saveClass(request);
        List<Map<String, Object>> classes = service.listClasses(null);

        assertEquals(saved.get("id"), classes.get(0).get("id"));
        assertEquals("CS-1", classes.get(0).get("classCode"));
    }

    @Test
    void deletesAnUnusedClass() {
        jdbc.update("INSERT INTO school_class(major_name,class_name,class_code,status) VALUES(?,?,?,1)",
                "Computer Science", "Class 1", "CS-1");
        long classId = jdbc.queryForObject("SELECT id FROM school_class WHERE class_code='CS-1'", Long.class);

        service.deleteClass(classId);

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM school_class WHERE id=?", Integer.class, classId));
    }

    @Test
    void refusesToDeleteAClassWithStudents() {
        jdbc.update("INSERT INTO school_class(major_name,class_name,class_code,status) VALUES(?,?,?,1)",
                "Computer Science", "Class 1", "CS-1");
        long classId = jdbc.queryForObject("SELECT id FROM school_class WHERE class_code='CS-1'", Long.class);
        jdbc.update("INSERT INTO school_student(student_no,full_name,class_id,status) VALUES(?,?,?,1)",
                "2026001", "Ada", classId);

        BusinessException error = assertThrows(BusinessException.class, () -> service.deleteClass(classId));

        assertTrue(error.getMessage().contains("不能删除"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM school_class WHERE id=?", Integer.class, classId));
    }

    @Test
    void deletesAStudentWithoutExamAttempts() {
        jdbc.update("INSERT INTO school_class(major_name,class_name,class_code,status) VALUES(?,?,?,1)",
                "Computer Science", "Class 1", "CS-1");
        long classId = jdbc.queryForObject("SELECT id FROM school_class WHERE class_code='CS-1'", Long.class);
        jdbc.update("INSERT INTO school_student(student_no,full_name,class_id,status) VALUES(?,?,?,1)",
                "2026001", "Ada", classId);
        long studentId = jdbc.queryForObject("SELECT id FROM school_student WHERE student_no='2026001'", Long.class);

        service.deleteStudent(studentId);

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM school_student WHERE id=?", Integer.class, studentId));
    }

    @Test
    void deletesAnExamThatHasNotBeenStarted() {
        long examId = seedDeletableExam();
        jdbc.update("INSERT INTO school_exam_knowledge_weight(job_id,knowledge_base_id,weight) VALUES(?,?,?)", 61L, 1L, 100);

        service.deleteExam(examId);

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM school_exam WHERE id=?", Integer.class, examId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM school_assessment_config WHERE id=61", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM school_exam_knowledge_weight WHERE job_id=61", Integer.class));
    }

    @Test
    void refusesToDeleteAnExamWithAnAttempt() {
        long examId = seedDeletableExam();
        jdbc.update("INSERT INTO sys_user(id,username,password,role_code,status) VALUES(?,?,?,?,1)",
                71L, "student_delete", "not-used", "STUDENT");
        jdbc.update("INSERT INTO school_class(id,major_name,class_name,class_code,status) VALUES(?,?,?,?,1)",
                71L, "Computer Science", "Class 71", "CS-71");
        jdbc.update("INSERT INTO school_student(id,student_no,full_name,class_id,user_id,status) VALUES(?,?,?,?,?,1)",
                71L, "DELETE-71", "Ada", 71L, 71L);
        jdbc.update("INSERT INTO school_exam_candidate(id,job_id,full_name,mobile_phone,major,application_status,interview_stage_status,interviewee_user_id) "
                        + "VALUES(?,?,?,?,?,?,?,?)", 71L, 61L, "Ada", "school-DELETE-71", "Computer Science", "EXAM_STARTED", "In progress", 71L);
        jdbc.update("INSERT INTO school_exam_process(id,recruitment_candidate_id,interviewee_user_id,job_id,current_stage,stage_status,overall_status,process_status_view) "
                        + "VALUES(?,?,?,?,?,?,?,?)", 71L, 71L, 71L, 61L, "AI", "IN_PROGRESS", "IN_PROGRESS", "答题中");
        jdbc.update("INSERT INTO school_exam_attempt(exam_id,student_id,process_id) VALUES(?,?,?)", examId, 71L, 71L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.deleteExam(examId));

        assertTrue(error.getMessage().contains("已有答题记录"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM school_exam WHERE id=?", Integer.class, examId));
    }

    @Test
    void publishingAnExistingExamUsesTheSchoolJobProjection() {
        jdbc.update("INSERT INTO school_assessment_config(id,job_code,job_title,department_name,requirements,responsibilities,publish_date,status) "
                        + "VALUES(?,?,?,?,?,?,?,1)", 81L, "EX-PUBLISH", "Draft exam", "All students", "", "School exam", "2026-08-16");
        jdbc.update("INSERT INTO school_exam(id,exam_code,exam_name,legacy_job_id,question_rounds,passing_score,status) "
                        + "VALUES(?,?,?,?,?,?,?)", 81L, "EX-PUBLISH", "Draft exam", 81L, 5, 60, "DRAFT");
        RecruitmentJob job = new RecruitmentJob();
        job.setId(81L);
        job.setJobCode("EX-PUBLISH");
        job.setJobTitle("Draft exam");
        job.setDepartmentName("All students");
        when(jobMapper.selectSchoolJobById(81L)).thenReturn(job);

        SchoolExamSaveRequest request = new SchoolExamSaveRequest();
        request.setId(81L);
        request.setExamCode("EX-PUBLISH");
        request.setExamName("Published exam");
        request.setStatus("PUBLISHED");

        Map<String, Object> saved = service.saveExam(request);

        assertEquals("Published exam", saved.get("examName"));
        verify(jobMapper).selectSchoolJobById(81L);
        verify(jobMapper).updateSchoolJob(job);
        verify(jobMapper, org.mockito.Mockito.never()).selectById(81L);
    }

    @Test
    void teacherCanToggleCameraAndScreenRecordingIndependently() {
        jdbc.update("INSERT INTO school_assessment_config(id,job_code,job_title,department_name,requirements,responsibilities,publish_date,status) "
                        + "VALUES(?,?,?,?,?,?,?,1)", 82L, "EX-MONITOR", "Exam", "All students", "", "School exam", "2026-08-16");
        jdbc.update("INSERT INTO school_exam(id,exam_code,exam_name,legacy_job_id,question_rounds,passing_score,status) "
                        + "VALUES(?,?,?,?,?,?,?)", 82L, "EX-MONITOR", "Exam", 82L, 5, 60, "DRAFT");
        RecruitmentJob job = new RecruitmentJob();
        job.setId(82L);
        when(jobMapper.selectSchoolJobById(82L)).thenReturn(job);
        SchoolExamSaveRequest request = new SchoolExamSaveRequest();
        request.setId(82L);
        request.setExamCode("EX-MONITOR");
        request.setExamName("Exam");
        request.setCameraEnabled(true);
        request.setScreenRecordingEnabled(false);

        Map<String, Object> saved = service.saveExam(request);

        assertEquals(1, ((Number) saved.get("cameraEnabled")).intValue());
        assertEquals(0, ((Number) saved.get("screenRecordingEnabled")).intValue());
        request.setCameraEnabled(null);
        request.setScreenRecordingEnabled(true);
        saved = service.saveExam(request);
        assertEquals(1, ((Number) saved.get("cameraEnabled")).intValue());
        assertEquals(1, ((Number) saved.get("screenRecordingEnabled")).intValue());
    }

    @Test
    void explicitMaximumRoundsPreserveFollowUpLimitAndCannotExceedIt() {
        jdbc.update("INSERT INTO school_assessment_config(id,job_code,job_title,department_name,requirements,responsibilities,publish_date,status) "
                        + "VALUES(?,?,?,?,?,?,?,1)", 81L, "EX-LIMIT", "Exam", "All students", "", "School exam", "2026-08-16");
        jdbc.update("INSERT INTO school_exam(id,exam_code,exam_name,legacy_job_id,question_rounds,passing_score,status) "
                        + "VALUES(?,?,?,?,?,?,?)", 81L, "EX-LIMIT", "Exam", 81L, 5, 60, "DRAFT");
        RecruitmentJob job = new RecruitmentJob();
        job.setId(81L);
        when(jobMapper.selectSchoolJobById(81L)).thenReturn(job);
        SchoolExamSaveRequest request = new SchoolExamSaveRequest();
        request.setId(81L);
        request.setExamCode("EX-LIMIT");
        request.setExamName("Exam");
        request.setQuestionRounds(3);
        request.setMaxQuestionRounds(4);
        request.setFollowUpRounds(20);

        Map<String, Object> saved = service.saveExam(request);

        assertEquals(3, saved.get("questionRounds"));
        assertEquals(4, saved.get("maxQuestionRounds"));
        assertEquals(20, saved.get("followUpRounds"));
        request.setMaxQuestionRounds(2);
        assertTrue(assertThrows(BusinessException.class, () -> service.saveExam(request))
                .getMessage().contains("最多答题轮数"));
        request.setMaxQuestionRounds(24);
        assertTrue(assertThrows(BusinessException.class, () -> service.saveExam(request))
                .getMessage().contains("最多答题轮数"));
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
        assertEquals("STUDENT", user.getValue().getRoleCode());

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
        jdbc.update("INSERT INTO school_answer_record(process_id,knowledge_point,question_content,question_status,answer_status,average_score,sequence_no) "
                        + "VALUES(?,?,?,?,?,?,?)", 41L, "Java", "Question 1", "READY", "COMPLETED", 80, 1);
        jdbc.update("INSERT INTO school_answer_record(process_id,knowledge_point,question_content,question_status,answer_status,average_score,sequence_no) "
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
        jdbc.update("INSERT INTO school_answer_record(process_id,knowledge_point,question_content,question_status,answer_status,average_score,sequence_no) "
                        + "VALUES(?,?,?,?,?,?,?)", 41L, "Java", "Question 1", "READY", "COMPLETED", 80, 1);

        List<Map<String, Object>> attempts = service.listStudentAttempts(88L);

        assertEquals(80, attempts.get(0).get("scoreRate"));
        assertNull(jdbc.queryForObject("SELECT score_rate FROM school_exam_attempt WHERE process_id=?", Integer.class, 41L));
        assertNull(jdbc.queryForObject("SELECT submitted_at FROM school_exam_attempt WHERE process_id=?", String.class, 41L));
    }

    @Test
    void classAnalyticsKeepsInProgressAttemptsVisibleWithoutUsingThemInScoreAggregates() {
        seedCompletedAttempt();
        jdbc.update("INSERT INTO school_answer_record(process_id,knowledge_point,question_content,question_status,answer_status,average_score,sequence_no) "
                        + "VALUES(?,?,?,?,?,?,?)", 41L, "Java", "Question 1", "READY", "COMPLETED", 80, 1);
        jdbc.update("INSERT INTO sys_user(id,username,password,role_code,status) VALUES(?,?,?,?,1)",
                89L, "student_2026002", "not-used", "STUDENT");
        jdbc.update("INSERT INTO school_student(id,student_no,full_name,class_id,user_id,status) VALUES(?,?,?,?,?,1)",
                10L, "2026002", "Grace", 1L, 89L);
        jdbc.update("INSERT INTO school_exam_candidate(id,job_id,full_name,mobile_phone,major,application_status,interview_stage_status,interviewee_user_id) "
                        + "VALUES(?,?,?,?,?,?,?,?)", 22L, 11L, "Grace", "school-2026002", "Computer Science", "EXAM_STARTED", "In progress", 89L);
        jdbc.update("INSERT INTO school_exam_process(id,recruitment_candidate_id,interviewee_user_id,job_id,current_stage,stage_status,overall_status,process_status_view) "
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
    void classAnalyticsIncludesTheStudentAntiCheatSwitchCount() {
        seedCompletedAttempt();
        jdbc.update("UPDATE school_exam_process SET anti_cheat_switch_count=? WHERE id=?", 3, 41L);

        Map<String, Object> analytics = service.analytics(31L, 1L);

        List<Map<String, Object>> students = rows(analytics.get("students"));
        assertEquals(3, ((Number) students.get(0).get("antiCheatSwitchCount")).intValue());
    }

    @Test
    void resettingAnAttemptClearsSubmissionMetadata() {
        seedCompletedAttempt();
        jdbc.update("UPDATE school_exam_attempt SET submitted_at=CURRENT_TIMESTAMP,score_rate=80,loss_rate=20,ai_summary='old' WHERE process_id=?", 41L);
        doAnswer(invocation -> {
            InterviewVO result = new InterviewVO();
            result.setId(41L);
            return result;
        }).when(interviewService).resetSchoolExamProcess(41L, true);

        service.resetAttempt(41L, true);

        assertNull(jdbc.queryForObject("SELECT submitted_at FROM school_exam_attempt WHERE process_id=?", String.class, 41L));
        assertNull(jdbc.queryForObject("SELECT score_rate FROM school_exam_attempt WHERE process_id=?", Integer.class, 41L));
        assertNull(jdbc.queryForObject("SELECT loss_rate FROM school_exam_attempt WHERE process_id=?", Integer.class, 41L));
        assertNull(jdbc.queryForObject("SELECT ai_summary FROM school_exam_attempt WHERE process_id=?", String.class, 41L));
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
        jdbc.update("INSERT INTO school_answer_record(process_id,knowledge_point,question_content,question_status,answer_content,answer_status,interviewer_score,scorer_score,average_score,interviewer_comment,sequence_no) "
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
    void scoreComparisonKeepsUntouchedQuestionsInBothExamTotals() {
        seedCompletedAttempt();
        jdbc.update("INSERT INTO school_answer_record(process_id,knowledge_point,question_content,answer_status,average_score,sequence_no) "
                + "VALUES(41,'Java','Q1','COMPLETED',20,1)");
        Long firstRecordId = jdbc.queryForObject("SELECT id FROM school_answer_record WHERE process_id=41 AND sequence_no=1", Long.class);
        jdbc.update("INSERT INTO school_answer_record(process_id,knowledge_point,question_content,answer_status,average_score,sequence_no) "
                + "VALUES(41,'Java','Q2','COMPLETED',60,2)");
        jdbc.update("INSERT INTO school_score_review(record_id,process_id,old_score,new_score,operator_user_id) VALUES(?,?,?,?,?)",
                firstRecordId, 41L, 80, 20, 88L);
        SessionUserVO admin = new SessionUserVO(); admin.setId(88L); admin.setRoleCode("IT_ADMIN");

        Map<String, Object> summary = service.searchScores(31L, null, null, null, admin).get(0);

        assertEquals(70, ((Number) summary.get("aiScore")).intValue());
        assertEquals(40, ((Number) summary.get("reviewedScore")).intValue());
    }

    @Test
    void assignedTeacherAloneCanSeeAndReviewExamScore() {
        seedCompletedAttempt();
        jdbc.update("INSERT INTO sys_user(id,username,password,role_code,status) VALUES(91,'teacher1','x','LECTURER',1)");
        jdbc.update("INSERT INTO sys_user(id,username,password,role_code,status) VALUES(92,'teacher2','x','LECTURER',1)");
        jdbc.update("INSERT INTO school_exam_teacher(exam_id,user_id) VALUES(31,91)");
        jdbc.update("INSERT INTO school_answer_record(process_id,knowledge_point,question_content,answer_content,answer_status,average_score,sequence_no) "
                + "VALUES(41,'Java','Question','Answer','COMPLETED',80,1)");
        Long recordId = jdbc.queryForObject("SELECT id FROM school_answer_record WHERE process_id=41", Long.class);
        SessionUserVO assigned = new SessionUserVO(); assigned.setId(91L); assigned.setRoleCode("LECTURER");
        SessionUserVO unassigned = new SessionUserVO(); unassigned.setId(92L); unassigned.setRoleCode("LECTURER");
        List<Map<String, Object>> initialScores = service.searchScores(31L, null, "Ada", null, assigned);
        assertEquals(1, initialScores.size());
        assertEquals(80, ((Number) initialScores.get(0).get("aiScore")).intValue());
        assertNull(initialScores.get(0).get("reviewedScore"));
        assertTrue(service.searchScores(31L, null, "Ada", null, unassigned).isEmpty());
        assertThrows(BusinessException.class, () -> service.adminAttemptDetails(41L, unassigned));
        ScoreReviewRequest review = new ScoreReviewRequest(); review.setScore(20); review.setNote("复核后发现关键步骤缺失");
        service.reviewScore(recordId, review, assigned);
        assertEquals(20, jdbc.queryForObject("SELECT average_score FROM school_answer_record WHERE id=?", Integer.class, recordId));
        assertEquals("REJECTED", jdbc.queryForObject("SELECT overall_status FROM school_exam_process WHERE id=41", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM school_score_review WHERE record_id=? AND old_score=80 AND new_score=20", Integer.class, recordId));
        review.setScore(30);
        service.reviewScore(recordId, review, assigned);
        Map<String, Object> summary = service.searchScores(31L, null, "Ada", null, assigned).get(0);
        assertEquals(80, ((Number) summary.get("aiScore")).intValue());
        assertEquals(30, ((Number) summary.get("reviewedScore")).intValue());
        Map<String, Object> reviewedRecord = rows(service.adminAttemptDetails(41L, assigned).get("records")).get(0);
        assertEquals(80, ((Number) reviewedRecord.get("aiScore")).intValue());
        assertEquals(30, ((Number) reviewedRecord.get("reviewedScore")).intValue());
        assertEquals("复核后发现关键步骤缺失", reviewedRecord.get("teacherNote"));
    }

    @Test
    void hiddenFinalScoreIsNotReturnedToStudent() {
        seedCompletedAttempt();
        jdbc.update("UPDATE school_exam SET show_final_score=0 WHERE id=31");
        assertNull(service.listStudentAttempts(88L).get(0).get("scoreRate"));
        assertThrows(BusinessException.class, () -> service.studentAttemptAnalysis(41L, 88L));
    }

    @Test
    void monitoringPolicyUsesAttemptSnapshotAndRequiresOwnership() {
        seedCompletedAttempt();
        jdbc.update("UPDATE school_exam_attempt SET camera_enabled=1,screen_recording_enabled=0 WHERE process_id=41");
        jdbc.update("UPDATE school_exam SET camera_enabled=0,screen_recording_enabled=1 WHERE id=31");
        Map<String, Object> policy = service.studentMonitoringPolicy(41L, 88L);
        assertEquals(1, ((Number) policy.get("camera_enabled")).intValue());
        assertEquals(0, ((Number) policy.get("screen_recording_enabled")).intValue());
        assertEquals(0, ((Number) policy.get("nextSegmentNo")).intValue());
        assertThrows(BusinessException.class, () -> service.studentMonitoringPolicy(999L, 88L));
    }

    @Test
    void startsLowPassMarkExamWithAMatchingFollowUpThreshold() {
        seedStartableExam(40);
        jdbc.update("UPDATE school_exam SET camera_enabled=1,screen_recording_enabled=0 WHERE id=31");
        doAnswer(invocation -> {
            RecruitmentCandidate candidate = invocation.getArgument(0);
            candidate.setId(21L);
            return 1;
        }).when(candidateMapper).insertSchoolCandidate(any(RecruitmentCandidate.class));
        InterviewVO started = new InterviewVO();
        started.setId(41L);
        when(interviewService.startInterviewProcess(any(StartInterviewProcessRequest.class))).thenReturn(started);

        Map<String, Object> result = service.startExam(31L, 88L);

        assertEquals(41L, result.get("processId"));
        jdbc.update("UPDATE school_exam SET camera_enabled=0,screen_recording_enabled=1 WHERE id=31");
        Map<String, Object> monitoring = jdbc.queryForMap("SELECT camera_enabled,screen_recording_enabled FROM school_exam_attempt WHERE process_id=41");
        assertEquals(1, ((Number) monitoring.get("camera_enabled")).intValue());
        assertEquals(0, ((Number) monitoring.get("screen_recording_enabled")).intValue());
        ArgumentCaptor<StartInterviewProcessRequest> request = ArgumentCaptor.forClass(StartInterviewProcessRequest.class);
        verify(interviewService).startInterviewProcess(request.capture());
        assertEquals(40, request.getValue().getAiThresholdScore());
        assertEquals(40, request.getValue().getAiFollowUpThreshold());
        assertEquals(1, request.getValue().getAiMinQuestionRounds());
        assertEquals(1, request.getValue().getAiMaxQuestionRounds());
    }

    @Test
    void startsExamWithTheConfiguredTotalRoundLimit() {
        seedStartableExam(60);
        jdbc.update("UPDATE school_exam SET question_rounds=2,follow_up_rounds=2,max_question_rounds=3 WHERE id=31");
        doAnswer(invocation -> {
            RecruitmentCandidate candidate = invocation.getArgument(0);
            candidate.setId(21L);
            return 1;
        }).when(candidateMapper).insertSchoolCandidate(any(RecruitmentCandidate.class));
        InterviewVO started = new InterviewVO();
        started.setId(41L);
        when(interviewService.startInterviewProcess(any(StartInterviewProcessRequest.class))).thenReturn(started);

        service.startExam(31L, 88L);

        ArgumentCaptor<StartInterviewProcessRequest> request = ArgumentCaptor.forClass(StartInterviewProcessRequest.class);
        verify(interviewService).startInterviewProcess(request.capture());
        assertEquals(2, request.getValue().getAiMinQuestionRounds());
        assertEquals(3, request.getValue().getAiMaxQuestionRounds());
    }

    private void seedStartableExam(int passingScore) {
        jdbc.update("INSERT INTO sys_user(id,username,password,role_code,status) VALUES(?,?,?,?,1)",
                88L, "student_2026002", "not-used", "STUDENT");
        jdbc.update("INSERT INTO school_class(id,major_name,class_name,class_code,status) VALUES(?,?,?,?,1)",
                1L, "Computer Science", "Class 2", "CS-2");
        jdbc.update("INSERT INTO school_student(id,student_no,full_name,class_id,user_id,status) VALUES(?,?,?,?,?,1)",
                9L, "2026002", "Grace", 1L, 88L);
        jdbc.update("INSERT INTO school_assessment_config(id,job_code,job_title,department_name,requirements,responsibilities,publish_date,status) "
                        + "VALUES(?,?,?,?,?,?,?,1)", 11L, "EX-2", "Exam", "Class 2", "", "", "2026-08-16");
        jdbc.update("INSERT INTO school_exam(id,exam_code,exam_name,class_id,legacy_job_id,question_rounds,passing_score,status) VALUES(?,?,?,?,?,?,?,?)",
                31L, "EX-2", "Low pass mark exam", 1L, 11L, 1, passingScore, "PUBLISHED");
    }

    private long seedDeletableExam() {
        jdbc.update("INSERT INTO school_assessment_config(id,job_code,job_title,department_name,requirements,responsibilities,publish_date,status) "
                        + "VALUES(?,?,?,?,?,?,?,1)", 61L, "DELETE-EXAM", "Delete exam", "All students", "", "School exam", "2026-08-16");
        jdbc.update("INSERT INTO school_exam(id,exam_code,exam_name,legacy_job_id,question_rounds,passing_score,status) VALUES(?,?,?,?,?,?,?)",
                61L, "DELETE-EXAM", "Delete exam", 61L, 5, 60, "DRAFT");
        return 61L;
    }

    private void seedCompletedAttempt() {
        jdbc.update("INSERT INTO sys_user(id,username,password,role_code,status) VALUES(?,?,?,?,1)",
                88L, "student_2026001", "not-used", "STUDENT");
        jdbc.update("INSERT INTO school_class(id,major_name,class_name,class_code,status) VALUES(?,?,?,?,1)",
                1L, "Computer Science", "Class 1", "CS-1");
        jdbc.update("INSERT INTO school_student(id,student_no,full_name,class_id,user_id,status) VALUES(?,?,?,?,?,1)",
                9L, "2026001", "Ada", 1L, 88L);
        jdbc.update("INSERT INTO school_assessment_config(id,job_code,job_title,department_name,requirements,responsibilities,publish_date,status) "
                        + "VALUES(?,?,?,?,?,?,?,1)", 11L, "EX-1", "Exam", "Class 1", "", "", "2026-08-16");
        jdbc.update("INSERT INTO school_exam_candidate(id,job_id,full_name,mobile_phone,major,application_status,interview_stage_status,interviewee_user_id) "
                        + "VALUES(?,?,?,?,?,?,?,?)", 21L, 11L, "Ada", "school-2026001", "Computer Science", "EXAM_STARTED", "In progress", 88L);
        jdbc.update("INSERT INTO school_exam_process(id,recruitment_candidate_id,interviewee_user_id,job_id,current_stage,stage_status,overall_status,process_status_view) "
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
