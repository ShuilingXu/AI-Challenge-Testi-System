package com.autohr.modules.school.service;

import com.autohr.common.exception.BusinessException;
import com.autohr.modules.interview.dto.AiAnswerRequest;
import com.autohr.modules.interview.dto.AntiCheatEventRequest;
import com.autohr.modules.interview.entity.InterviewProcess;
import com.autohr.modules.interview.mapper.InterviewProcessMapper;
import com.autohr.modules.interview.service.impl.InterviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

class StudentProcessAccessTest {
    @TempDir Path directory;
    JdbcTemplate jdbc;
    InterviewServiceImpl service;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:" + directory.resolve("access.db")));
        jdbc.execute("CREATE TABLE school_class(id INTEGER PRIMARY KEY,status INTEGER)");
        jdbc.execute("CREATE TABLE school_student(id INTEGER PRIMARY KEY,class_id INTEGER,user_id INTEGER,status INTEGER)");
        jdbc.execute("CREATE TABLE school_exam_attempt(process_id INTEGER,student_id INTEGER,exam_id INTEGER)");
        jdbc.execute("CREATE TABLE school_exam(id INTEGER,show_live_score INTEGER,show_final_score INTEGER)");
        jdbc.execute("CREATE TABLE school_exam_process(id INTEGER,overall_status TEXT)");
        jdbc.update("INSERT INTO school_class VALUES(1,1)");
        jdbc.update("INSERT INTO school_student VALUES(9,1,88,1)");
        jdbc.update("INSERT INTO school_exam_attempt VALUES(41,9,31)");
        jdbc.update("INSERT INTO school_exam VALUES(31,1,1)");
        jdbc.update("INSERT INTO school_exam_process VALUES(41,'COMPLETED')");
        InterviewProcess process = new InterviewProcess();
        process.setId(41L);
        process.setIntervieweeUserId(88L);
        process.setOverallStatus("COMPLETED");
        InterviewProcessMapper mapper = mock(InterviewProcessMapper.class);
        when(mapper.selectById(41L)).thenReturn(process);
        service = mock(InterviewServiceImpl.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "processMapper", mapper);
        ReflectionTestUtils.setField(service, "schoolScoreJdbc", jdbc);
    }

    @Test
    void activeRosterCanUseOwnedProcessAndOtherStudentsCannot() {
        assertEquals(41L, service.heartbeat(41L, 88L).getProcessId());
        assertThrows(BusinessException.class, () -> service.heartbeat(41L, 89L));
    }

    @Test
    void disabledRosterBlocksEveryStudentExecutionEntryPoint() {
        jdbc.update("UPDATE school_student SET status=0 WHERE id=9");
        assertExecutionBlocked();
    }

    @Test
    void disabledClassBlocksEveryStudentExecutionEntryPoint() {
        jdbc.update("UPDATE school_class SET status=0 WHERE id=1");
        assertExecutionBlocked();
    }

    @Test
    void changedRosterBindingInvalidatesOldProcessOwner() {
        jdbc.update("UPDATE school_student SET user_id=89 WHERE id=9");
        assertExecutionBlocked();
    }

    @Test
    void hiddenScoresMaskPassFailAndThresholdsOnProcessAndHeartbeat() {
        jdbc.update("UPDATE school_exam SET show_live_score=0,show_final_score=0 WHERE id=31");
        var mapper = (InterviewProcessMapper) ReflectionTestUtils.getField(service, "processMapper");
        var process = mapper.selectById(41L);
        process.setAiThresholdScore(60); process.setAiFollowUpThreshold(50); process.setAiAverageScore(45);
        for (String status : java.util.List.of("REJECTED", "COMPLETED")) {
            process.setOverallStatus(status); process.setStageStatus(status);
            process.setProcessStatusView(status.equals("REJECTED") ? "考试未达到及格线" : "考试已完成");
            jdbc.update("UPDATE school_exam_process SET overall_status=?", status);
            for (var result : java.util.List.of(service.getIntervieweeProcess(41L, 88L), service.heartbeat(41L, 88L))) {
                assertEquals("答题已提交", result.getProcessStatusView());
                assertEquals("COMPLETED", result.getOverallStatus());
                assertEquals("COMPLETED", result.getStageStatus());
                org.junit.jupiter.api.Assertions.assertNull(result.getAiAverageScore());
                org.junit.jupiter.api.Assertions.assertNull(result.getAiThresholdScore());
                org.junit.jupiter.api.Assertions.assertNull(result.getAiFollowUpThreshold());
            }
        }
        jdbc.update("UPDATE school_exam SET show_final_score=1");
        assertEquals(45, service.getIntervieweeProcess(41L, 88L).getAiAverageScore());
    }

    private void assertExecutionBlocked() {
        AiAnswerRequest answer = new AiAnswerRequest();
        answer.setProcessId(41L);
        AntiCheatEventRequest event = new AntiCheatEventRequest();
        event.setProcessId(41L);
        assertThrows(BusinessException.class, () -> service.getIntervieweeProcess(41L, 88L));
        assertThrows(BusinessException.class, () -> service.heartbeat(41L, 88L));
        assertThrows(BusinessException.class, () -> service.getIntervieweeNextAiQuestion(41L, 88L));
        assertThrows(BusinessException.class, () -> service.listIntervieweeAiRecords(41L, 88L));
        assertThrows(BusinessException.class, () -> service.submitIntervieweeAiAnswer(answer, 88L));
        assertThrows(BusinessException.class, () -> service.submitIntervieweeAiAnswerStream(answer, 88L));
        assertThrows(BusinessException.class, () -> service.reportAntiCheatEvent(event, 88L, "Student"));
    }
}
