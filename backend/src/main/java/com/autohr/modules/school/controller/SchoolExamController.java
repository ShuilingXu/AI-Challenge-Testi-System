package com.autohr.modules.school.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.modules.auth.config.AuthCookieService;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.auth.service.AuthRateLimitService;
import com.autohr.modules.school.dto.SchoolClassSaveRequest;
import com.autohr.modules.school.dto.SchoolExamSaveRequest;
import com.autohr.modules.school.dto.SchoolStudentSaveRequest;
import com.autohr.modules.school.dto.StudentRegistrationRequest;
import com.autohr.modules.school.service.SchoolExamService;
import com.autohr.modules.school.service.SchoolExamRecordingService;
import com.autohr.modules.school.dto.ScoreReviewRequest;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class SchoolExamController {

    private final SchoolExamService schoolExamService;
    private final SchoolExamRecordingService schoolExamRecordingService;
    private final AuthService authService;
    private final AuthCookieService authCookieService;
    private final AuthRateLimitService authRateLimitService;

    @GetMapping("/classes")
    public ApiResponse<List<Map<String, Object>>> classes() {
        return ApiResponse.success(schoolExamService.listPublicClasses());
    }

    @PostMapping("/student-registration")
    public ApiResponse<Map<String, Object>> studentRegistration(@Valid @RequestBody StudentRegistrationRequest request,
                                                                  HttpServletRequest httpRequest,
                                                                  HttpServletResponse response) {
        authRateLimitService.checkStudentEntry(httpRequest, request.getStudentNo());
        Map<String, Object> result = schoolExamService.registerStudent(request);
        Object token = result.get("token");
        if (token instanceof String value && !value.isBlank()) {
            authCookieService.write(response, value);
        }
        return ApiResponse.success(Map.of("user", result.get("user")));
    }

    @GetMapping("/admin/classes")
    public ApiResponse<List<Map<String, Object>>> listClasses(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(schoolExamService.listClasses(keyword));
    }

    @PostMapping("/admin/classes")
    public ApiResponse<Map<String, Object>> saveClass(@Valid @RequestBody SchoolClassSaveRequest request) {
        return ApiResponse.success(schoolExamService.saveClass(request));
    }

    @PostMapping("/admin/classes/{classId}/delete")
    public ApiResponse<Void> deleteClass(@PathVariable Long classId) {
        schoolExamService.deleteClass(classId);
        return ApiResponse.success("deleted", null);
    }

    @PostMapping("/admin/classes/import")
    public ApiResponse<Map<String, Object>> importClasses(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(schoolExamService.importClasses(file));
    }

    @GetMapping("/admin/classes/template")
    public ResponseEntity<byte[]> classesTemplate() {
        return templateResponse("classes-import-template.xls", schoolExamService.classesTemplate());
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

    @PostMapping("/admin/students/{studentId}/delete")
    public ApiResponse<Void> deleteStudent(@PathVariable Long studentId) {
        schoolExamService.deleteStudent(studentId);
        return ApiResponse.success("deleted", null);
    }

    @PostMapping("/admin/students/import")
    public ApiResponse<Map<String, Object>> importStudents(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(schoolExamService.importStudents(file));
    }

    @GetMapping("/admin/students/template")
    public ResponseEntity<byte[]> studentsTemplate() {
        return templateResponse("students-import-template.xls", schoolExamService.studentsTemplate());
    }

    @GetMapping("/admin/exams")
    public ApiResponse<List<Map<String, Object>>> listExams(Authentication authentication) {
        return ApiResponse.success(schoolExamService.listAdminExams(current(authentication)));
    }

    @GetMapping("/admin/teachers")
    public ApiResponse<List<Map<String, Object>>> teachers(Authentication authentication) {
        return ApiResponse.success(schoolExamService.listAssignableTeachers(current(authentication)));
    }

    @PostMapping("/admin/exams")
    public ApiResponse<Map<String, Object>> saveExam(Authentication authentication, @Valid @RequestBody SchoolExamSaveRequest request) {
        return ApiResponse.success(schoolExamService.saveExam(request, current(authentication)));
    }

    @PostMapping("/admin/exams/{examId}/delete")
    public ApiResponse<Void> deleteExam(Authentication authentication, @PathVariable Long examId) {
        schoolExamService.deleteExam(examId, current(authentication));
        return ApiResponse.success("deleted", null);
    }

    @GetMapping("/admin/analytics")
    public ApiResponse<Map<String, Object>> analytics(Authentication authentication, @RequestParam(required = false) Long examId,
                                                        @RequestParam(required = false) Long classId) {
        return ApiResponse.success(schoolExamService.analytics(examId, classId, current(authentication)));
    }

    @GetMapping("/admin/scores")
    public ApiResponse<List<Map<String, Object>>> scores(Authentication authentication,
            @RequestParam(required = false) Long examId, @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String name, @RequestParam(required = false) String studentNo) {
        return ApiResponse.success(schoolExamService.searchScores(examId, classId, name, studentNo, current(authentication)));
    }

    @PostMapping("/admin/records/{recordId}/review")
    public ApiResponse<Map<String, Object>> reviewScore(Authentication authentication, @PathVariable Long recordId,
            @Valid @RequestBody ScoreReviewRequest request) {
        return ApiResponse.success(schoolExamService.reviewScore(recordId, request, current(authentication)));
    }

    @GetMapping("/admin/attempts/{processId}")
    public ApiResponse<Map<String, Object>> adminAttemptDetails(Authentication authentication, @PathVariable Long processId) {
        return ApiResponse.success(schoolExamService.adminAttemptDetails(processId, current(authentication)));
    }

    @PostMapping("/admin/attempts/{processId}/restart")
    public ApiResponse<Map<String, Object>> restartAttempt(Authentication authentication, @PathVariable Long processId) {
        return ApiResponse.success(schoolExamService.resetAttempt(processId, true, current(authentication)));
    }

    @PostMapping("/admin/attempts/{processId}/continue")
    public ApiResponse<Map<String, Object>> continueAttempt(Authentication authentication, @PathVariable Long processId) {
        return ApiResponse.success(schoolExamService.resetAttempt(processId, false, current(authentication)));
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

    @GetMapping("/student/attempts/{processId}/monitoring-policy")
    public ApiResponse<Map<String, Object>> monitoringPolicy(Authentication authentication, @PathVariable Long processId) {
        return ApiResponse.success(schoolExamService.studentMonitoringPolicy(processId, current(authentication).getId()));
    }

    @PostMapping("/student/attempts/{processId}/recordings/{segmentNo}")
    public ApiResponse<Void> uploadRecording(Authentication authentication, @PathVariable Long processId,
            @PathVariable int segmentNo, @RequestParam("file") MultipartFile file) {
        schoolExamRecordingService.upload(processId, segmentNo, current(authentication).getId(), file);
        return ApiResponse.success(null);
    }

    @GetMapping("/admin/attempts/{processId}/recordings")
    public ApiResponse<List<Map<String, Object>>> recordings(Authentication authentication, @PathVariable Long processId) {
        return ApiResponse.success(schoolExamRecordingService.list(processId, current(authentication)));
    }

    @GetMapping("/admin/attempts/{processId}/recordings/{segmentNo}")
    public ResponseEntity<Resource> openRecording(Authentication authentication, @PathVariable Long processId, @PathVariable int segmentNo) {
        return schoolExamRecordingService.open(processId, segmentNo, current(authentication));
    }

    @GetMapping("/admin/attempts/{processId}/recordings/{segmentNo}/download-url")
    public ApiResponse<Map<String, String>> recordingDownloadUrl(Authentication authentication,
            @PathVariable Long processId, @PathVariable int segmentNo) {
        schoolExamRecordingService.open(processId, segmentNo, current(authentication));
        return ApiResponse.success(Map.of());
    }

    private SessionUserVO current(Authentication authentication) {
        return authService.loadUserByUsername(authentication.getName());
    }

    private ResponseEntity<byte[]> templateResponse(String filename, byte[] content) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                .body(content);
    }
}
