package com.autohr.modules.school.controller;

import com.autohr.config.SecurityConfig;
import com.autohr.modules.auth.config.AuthCookieService;
import com.autohr.modules.auth.config.JwtAuthenticationFilter;
import com.autohr.modules.auth.config.PasswordChangeRequiredFilter;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.school.service.SchoolExamService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SchoolExamController.class)
@Import(SecurityConfig.class)
class SchoolExamControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    SchoolExamService schoolExamService;

    @MockBean
    AuthService authService;

    @MockBean
    AuthCookieService authCookieService;

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
    void anonymousUsersCanListPublicClassesButNotAdminImports() throws Exception {
        when(schoolExamService.listPublicClasses()).thenReturn(List.of(Map.of("id", 1, "className", "Class 1")));

        mockMvc.perform(get("/api/exams/classes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].className").value("Class 1"));

        mockMvc.perform(multipart("/api/exams/admin/classes/import")
                        .file("file", new byte[]{1}))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/exams/admin/classes/import")
                        .file("file", new byte[]{1})
                        .cookie(new Cookie("AUTOHR_CSRF", "test-csrf-token"))
                        .header("X-CSRF-Token", "test-csrf-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR_USER")
    void authorizedAdministratorsCanForwardStudentImportFiles() throws Exception {
        when(schoolExamService.importStudents(any())).thenReturn(Map.of("successCount", 1, "failureCount", 0, "rows", List.of()));

        mockMvc.perform(multipart("/api/exams/admin/students/import")
                        .file(new MockMultipartFile("file", "students.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1}))
                        .cookie(new Cookie("AUTOHR_CSRF", "test-csrf-token"))
                        .header("X-CSRF-Token", "test-csrf-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(1));

        verify(schoolExamService).importStudents(any());
    }

    @Test
    @WithMockUser(username = "student_2026001", authorities = "ROLE_INTERVIEWEE")
    void studentsCanStartOnlyTheirOwnExamSession() throws Exception {
        SessionUserVO student = new SessionUserVO();
        student.setId(88L);
        when(authService.loadUserByUsername("student_2026001")).thenReturn(student);
        when(schoolExamService.startExam(31L, 88L)).thenReturn(Map.of("processId", 41L, "resumed", false));

        mockMvc.perform(post("/api/exams/student/exams/31/start")
                        .cookie(new Cookie("AUTOHR_CSRF", "test-csrf-token"))
                        .header("X-CSRF-Token", "test-csrf-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processId").value(41));

        verify(schoolExamService).startExam(31L, 88L);
    }

    @Test
    void rosterRegistrationWritesTheStudentSessionCookie() throws Exception {
        SessionUserVO student = new SessionUserVO();
        student.setRoleCode("INTERVIEWEE");
        when(schoolExamService.registerStudent(any())).thenReturn(Map.of("token", "student-session-token", "user", student));

        mockMvc.perform(post("/api/exams/student-registration")
                        .contentType("application/json")
                        .content("{\"classId\":1,\"fullName\":\"Student\",\"studentNo\":\"20260001\"}")
                        .cookie(new Cookie("AUTOHR_CSRF", "test-csrf-token"))
                        .header("X-CSRF-Token", "test-csrf-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.roleCode").value("INTERVIEWEE"));

        verify(authCookieService).write(any(), eq("student-session-token"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR_ADMIN")
    void administratorsCannotAccessStudentAttemptEndpoints() throws Exception {
        mockMvc.perform(get("/api/exams/student/attempts"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTERVIEWEE")
    void studentsCannotAccessRetiredVideoInterviewEndpoints() throws Exception {
        mockMvc.perform(get("/api/interview/interviewee/video-state/41"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR_ADMIN")
    void legacyRecruitmentAndHrRoutesAreBlockedForSchoolAdministrators() throws Exception {
        mockMvc.perform(get("/api/recruitment/admin/jobs"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/hr/dashboard"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/interview/hr/processes"))
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
