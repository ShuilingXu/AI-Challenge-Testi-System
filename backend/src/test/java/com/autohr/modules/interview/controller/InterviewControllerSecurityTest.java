package com.autohr.modules.interview.controller;

import com.autohr.common.file.S3ObjectStorageService;
import com.autohr.config.SecurityConfig;
import com.autohr.modules.auth.config.JwtAuthenticationFilter;
import com.autohr.modules.auth.config.PasswordChangeRequiredFilter;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.service.AuditLogService;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.interview.dto.InterviewVO;
import com.autohr.modules.interview.service.InterviewService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterviewController.class)
@Import(SecurityConfig.class)
class InterviewControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    InterviewService interviewService;

    @MockBean
    AuthService authService;

    @MockBean
    AuditLogService auditLogService;

    @MockBean
    S3ObjectStorageService s3ObjectStorageService;

    @MockBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    PasswordChangeRequiredFilter passwordChangeRequiredFilter;

    @BeforeEach
    void continueThroughCustomFilters() throws Exception {
        continueFilter(jwtAuthenticationFilter);
        continueFilter(passwordChangeRequiredFilter);
    }

    @Test
    @WithMockUser(username = "student_2026001", authorities = "ROLE_INTERVIEWEE")
    void studentsCanReportAntiCheatEventsForTheirOwnProcess() throws Exception {
        SessionUserVO student = new SessionUserVO();
        student.setId(88L);
        student.setDisplayName("Ada");
        when(authService.loadUserByUsername("student_2026001")).thenReturn(student);
        when(interviewService.reportAntiCheatEvent(any(), eq(88L), eq("Ada"))).thenReturn(new InterviewVO());

        mockMvc.perform(post("/api/interview/interviewee/anti-cheat-event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processId\":41,\"eventType\":\"TAB_HIDDEN\",\"eventId\":\"event-1\",\"occurredAtEpochMillis\":" + Instant.now().toEpochMilli() + "}")
                        .cookie(new jakarta.servlet.http.Cookie("AUTOHR_CSRF", "test-csrf-token"))
                        .header("X-CSRF-Token", "test-csrf-token"))
                .andExpect(status().isOk());

        verify(interviewService).reportAntiCheatEvent(any(), eq(88L), eq("Ada"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR_ADMIN")
    void administratorsCannotSubmitStudentAntiCheatEvents() throws Exception {
        mockMvc.perform(post("/api/interview/interviewee/anti-cheat-event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processId\":41,\"eventType\":\"TAB_HIDDEN\",\"eventId\":\"event-1\",\"occurredAtEpochMillis\":" + Instant.now().toEpochMilli() + "}")
                        .cookie(new jakarta.servlet.http.Cookie("AUTOHR_CSRF", "test-csrf-token"))
                        .header("X-CSRF-Token", "test-csrf-token"))
                .andExpect(status().isForbidden());
    }

    private void continueFilter(org.springframework.web.filter.OncePerRequestFilter filter) throws Exception {
        doAnswer(invocation -> {
            invocation.<FilterChain>getArgument(2).doFilter(
                    invocation.<ServletRequest>getArgument(0),
                    invocation.<ServletResponse>getArgument(1));
            return null;
        }).when(filter).doFilter(any(), any(), any());
    }
}
