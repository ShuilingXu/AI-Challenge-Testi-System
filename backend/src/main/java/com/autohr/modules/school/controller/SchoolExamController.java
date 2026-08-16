package com.autohr.modules.school.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.modules.auth.config.AuthCookieService;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.school.dto.SchoolClassSaveRequest;
import com.autohr.modules.school.dto.SchoolExamSaveRequest;
import com.autohr.modules.school.dto.SchoolStudentSaveRequest;
import com.autohr.modules.school.dto.StudentRegistrationRequest;
import com.autohr.modules.school.service.SchoolExamService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class SchoolExamController {

    private final SchoolExamService schoolExamService;
    private final AuthService authService;
    private final AuthCookieService authCookieService;

    @GetMapping("/classes")
    public ApiResponse<List<Map<String, Object>>> classes() {
        return ApiResponse.success(schoolExamService.listPublicClasses());
    }

    @PostMapping("/student-registration")
    public ApiResponse<Map<String, Object>> studentRegistration(@Valid @RequestBody StudentRegistrationRequest request,
                                                                  HttpServletResponse response) {
        Map<String, Object> result = schoolExamService.registerStudent(request);
        Object token = result.get("token");
        if (token instanceof String value && !value.isBlank()) {
            authCookieService.write(response, value);
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/admin/classes")
    public ApiResponse<List<Map<String, Object>>> listClasses(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(schoolExamService.listClasses(keyword));
    }

    @PostMapping("/admin/classes")
    public ApiResponse<Map<String, Object>> saveClass(@Valid @RequestBody SchoolClassSaveRequest request) {
        return ApiResponse.success(schoolExamService.saveClass(request));
    }

    @PostMapping("/admin/classes/import")
    public ApiResponse<Map<String, Object>> importClasses(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(schoolExamService.importClasses(file));
    }

    @GetMapping("/admin/students")
    public ApiResponse<List<Map<String, Object>>> listStudents(@RequestParam(required = false) Long classId,
                                                                 @RequestParam(required = false) String keyword) {
        return ApiResponse.success(schoolExamService.listStudents(classId, keyword));
    }

    @PostMapping("/admin/students")
    public ApiResponse<Map<String, Object>> saveStudent(@Valid @RequestBody SchoolStudentSaveRequest request) {
        return ApiResponse.success(schoolExamService.saveStudent(request));
    }

    @PostMapping("/admin/students/import")
    public ApiResponse<Map<String, Object>> importStudents(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(schoolExamService.importStudents(file));
    }

    @GetMapping("/admin/exams")
    public ApiResponse<List<Map<String, Object>>> listExams() {
        return ApiResponse.success(schoolExamService.listAdminExams());
    }

    @PostMapping("/admin/exams")
    public ApiResponse<Map<String, Object>> saveExam(@Valid @RequestBody SchoolExamSaveRequest request) {
        return ApiResponse.success(schoolExamService.saveExam(request));
    }

    @GetMapping("/admin/analytics")
    public ApiResponse<Map<String, Object>> analytics(@RequestParam(required = false) Long examId,
                                                        @RequestParam(required = false) Long classId) {
        return ApiResponse.success(schoolExamService.analytics(examId, classId));
    }

    @GetMapping("/student/exams")
    public ApiResponse<List<Map<String, Object>>> studentExams(Authentication authentication) {
        return ApiResponse.success(schoolExamService.listStudentExams(current(authentication).getId()));
    }

    @PostMapping("/student/exams/{examId}/start")
    public ApiResponse<Map<String, Object>> startExam(Authentication authentication, @PathVariable Long examId) {
        return ApiResponse.success(schoolExamService.startExam(examId, current(authentication).getId()));
    }

    @GetMapping("/student/attempts")
    public ApiResponse<List<Map<String, Object>>> attempts(Authentication authentication) {
        return ApiResponse.success(schoolExamService.listStudentAttempts(current(authentication).getId()));
    }

    @GetMapping("/student/attempts/{processId}/analysis")
    public ApiResponse<Map<String, Object>> attemptAnalysis(Authentication authentication, @PathVariable Long processId) {
        return ApiResponse.success(schoolExamService.studentAttemptAnalysis(processId, current(authentication).getId()));
    }

    private SessionUserVO current(Authentication authentication) {
        return authService.loadUserByUsername(authentication.getName());
    }
}
