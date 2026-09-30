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
