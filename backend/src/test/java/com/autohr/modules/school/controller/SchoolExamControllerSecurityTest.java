package com.autohr.modules.school.controller;

import com.autohr.config.SecurityConfig;
import com.autohr.config.SpaController;
import com.autohr.modules.auth.config.AuthCookieService;
import com.autohr.modules.auth.config.JwtAuthenticationFilter;
import com.autohr.modules.auth.config.PasswordChangeRequiredFilter;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.auth.service.AuthRateLimitService;
import com.autohr.modules.school.service.SchoolExamService;
import com.autohr.modules.school.service.SchoolExamRecordingService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import com.autohr.common.exception.BusinessException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;

@WebMvcTest({SchoolExamController.class, SpaController.class})
@Import(SecurityConfig.class)
class SchoolExamControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    SchoolExamService schoolExamService;

    @MockBean
    SchoolExamRecordingService schoolExamRecordingService;

    @MockBean
    AuthService authService;

    @MockBean
    AuthCookieService authCookieService;

    @MockBean
    AuthRateLimitService authRateLimitService;

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
    void embeddedFrontendAllowsEntryAndHistoryRoutesWithoutOpeningPrivateApis() throws Exception {
        org.junit.jupiter.api.Assertions.assertArrayEquals(SpaController.ROUTES,
                SpaController.class.getMethod("frontend").getAnnotation(org.springframework.web.bind.annotation.GetMapping.class).value());
        for (String path : List.of("/", "/login", "/student/register", "/admin/exams", "/exam/take/41", "/admin/score-review/41")) {
            mockMvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
        mockMvc.perform(get("/api/exams/admin/exams")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/hr/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR_USER")
    void authorizedAdministratorsCanForwardStudentImportFiles() throws Exception {
        SessionUserVO actor = new SessionUserVO(); actor.setId(91L); actor.setRoleCode("HR_ADMIN");
        when(authService.loadUserByUsername("user")).thenReturn(actor);
        when(schoolExamService.importStudents(any(), eq(actor))).thenReturn(Map.of("successCount", 1, "failureCount", 0, "rows", List.of()));

        mockMvc.perform(multipart("/api/exams/admin/students/import")
                        .file(new MockMultipartFile("file", "students.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1}))
                        .cookie(new Cookie("AUTOHR_CSRF", "test-csrf-token"))
                        .header("X-CSRF-Token", "test-csrf-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(1));

        verify(schoolExamService).importStudents(any(), eq(actor));
    }

    @Test
    @WithMockUser(username = "student_2026001", authorities = "ROLE_STUDENT")
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
    @WithMockUser(username = "teacher", authorities = "ROLE_LECTURER")
    void rosterEndpointsPassTheAuthenticatedTeacherToScopeChecks() throws Exception {
        var teacher = new SessionUserVO(); teacher.setId(91L); teacher.setRoleCode("LECTURER");
        when(authService.loadUserByUsername("teacher")).thenReturn(teacher);
        when(schoolExamService.listClasses(null, teacher)).thenReturn(List.of());
        when(schoolExamService.listStudents(1L, "Ada", teacher)).thenReturn(List.of());
        mockMvc.perform(get("/api/exams/admin/classes")).andExpect(status().isOk());
        mockMvc.perform(get("/api/exams/admin/students").param("classId", "1").param("keyword", "Ada")).andExpect(status().isOk());
        verify(schoolExamService).listClasses(null, teacher);
        verify(schoolExamService).listStudents(1L, "Ada", teacher);
        doThrow(new BusinessException("仅管理员可维护班级或删除名册")).when(schoolExamService).deleteStudent(9L, teacher);
        mockMvc.perform(post("/api/exams/admin/students/9/delete")
                .cookie(new Cookie("AUTOHR_CSRF", "test-csrf-token"))
                .header("X-CSRF-Token", "test-csrf-token")).andExpect(status().isBadRequest());
        verify(schoolExamService).deleteStudent(9L, teacher);
        verify(schoolExamService, never()).deleteStudent(9L);
    }

    @Test
    @WithMockUser(username = "teacher", authorities = "ROLE_LECTURER")
    void rosterMutationWithoutCsrfTokenNeverReachesService() throws Exception {
        mockMvc.perform(post("/api/exams/admin/students/9/delete")).andExpect(status().isForbidden());
        verify(schoolExamService, never()).deleteStudent(any(), any());
    }

    @Test
    void rosterRegistrationWritesTheStudentSessionCookie() throws Exception {
        SessionUserVO student = new SessionUserVO();
        student.setRoleCode("STUDENT");
        when(schoolExamService.registerStudent(any())).thenReturn(Map.of("token", "student-session-token", "user", student));

        mockMvc.perform(post("/api/exams/student-registration")
                        .contentType("application/json")
                        .content("{\"classId\":1,\"fullName\":\"Student\",\"studentNo\":\"20260001\"}")
                        .cookie(new Cookie("AUTOHR_CSRF", "test-csrf-token"))
                        .header("X-CSRF-Token", "test-csrf-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.user.roleCode").value("STUDENT"));

        verify(authCookieService).write(any(), eq("student-session-token"));
        verify(authRateLimitService).checkStudentEntry(any(), eq("20260001"));
    }

    @Test
    void rateLimitedStudentEntryDoesNotAuthenticateOrIssueACookie() throws Exception {
        doThrow(new BusinessException("该学号登记尝试过于频繁，请稍后重试"))
                .when(authRateLimitService).checkStudentEntry(any(), eq("20260001"));
        mockMvc.perform(post("/api/exams/student-registration")
                        .contentType("application/json")
                        .content("{\"classId\":1,\"fullName\":\"Student\",\"studentNo\":\"20260001\"}")
                        .cookie(new Cookie("AUTOHR_CSRF", "test-csrf-token"))
                        .header("X-CSRF-Token", "test-csrf-token"))
                .andExpect(status().isBadRequest());
        verify(schoolExamService, never()).registerStudent(any());
        verify(authCookieService, never()).write(any(), any());
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR_ADMIN")
    void administratorsCannotAccessStudentAttemptEndpoints() throws Exception {
        mockMvc.perform(get("/api/exams/student/attempts"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_STUDENT")
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
