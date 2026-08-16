package com.autohr.modules.recruitment.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.common.api.PageQuery;
import com.autohr.common.api.PageResponse;
import com.autohr.common.file.DownloadUrlResponse;
import com.autohr.common.file.FileDownloadSupport;
import com.autohr.common.file.S3ObjectStorageService;
import com.autohr.common.file.UploadPaths;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.auth.service.AuditLogService;
import com.autohr.modules.recruitment.dto.CandidateApplyRequest;
import com.autohr.modules.recruitment.dto.CandidateVO;
import com.autohr.modules.recruitment.dto.JobSaveRequest;
import com.autohr.modules.recruitment.dto.JobVO;
import com.autohr.modules.recruitment.dto.ResumeFileVO;
import com.autohr.modules.recruitment.entity.RecruitmentResumeFile;
import com.autohr.modules.recruitment.service.RecruitmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/recruitment")
@RequiredArgsConstructor
public class RecruitmentController {

    private final RecruitmentService recruitmentService;
    private final AuthService authService;
    private final AuditLogService auditLogService;
    private final S3ObjectStorageService s3ObjectStorageService;

    @PostMapping("/admin/jobs")
    public ApiResponse<JobVO> saveJob(Authentication authentication,
                                      @Valid @RequestBody JobSaveRequest request) {
        JobVO saved = recruitmentService.saveJob(request);
        SessionUserVO current = currentUser(authentication);
        auditLogService.log(current.getId(), current.getDisplayName(), current.getRoleCode(), "ADMIN", request.getId() == null ? "CREATE_RECRUITMENT_JOB" : "UPDATE_RECRUITMENT_JOB", "RECRUITMENT_JOB", String.valueOf(saved.getId()), saved.getJobTitle());
        return ApiResponse.success(saved);
    }

    @GetMapping("/admin/jobs")
    public ApiResponse<PageResponse<JobVO>> listAdminJobs(@RequestParam(required = false) Integer status,
                                                  @RequestParam(required = false) String departmentName,
                                                  @RequestParam(required = false) String jobType,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(recruitmentService.listJobs(status, departmentName, jobType, keyword,
                PageQuery.of(page, pageSize)));
    }

    @DeleteMapping("/admin/jobs/{id}")
    public ApiResponse<Void> deleteJob(Authentication authentication,
                                       @PathVariable Long id) {
        recruitmentService.deleteJob(id);
        SessionUserVO current = currentUser(authentication);
        auditLogService.log(current.getId(), current.getDisplayName(), current.getRoleCode(), "ADMIN", "DELETE_RECRUITMENT_JOB", "RECRUITMENT_JOB", String.valueOf(id), "删除招聘岗位");
        return ApiResponse.success("deleted", null);
    }

    @GetMapping("/admin/candidates")
    public ApiResponse<PageResponse<CandidateVO>> listCandidates(@RequestParam(required = false) Long jobId,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(required = false) String interviewStageStatus,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(recruitmentService.listCandidates(jobId, status, interviewStageStatus, keyword,
                PageQuery.of(page, pageSize)));
    }

    @GetMapping("/admin/candidates/{id}")
    public ApiResponse<CandidateVO> getCandidate(@PathVariable Long id) {
        return ApiResponse.success(recruitmentService.getCandidate(id));
    }

    @PostMapping("/admin/candidates/{id}/reject-resume")
    public ApiResponse<CandidateVO> rejectCandidateResume(Authentication authentication,
                                                           @PathVariable Long id) {
        CandidateVO rejected = recruitmentService.rejectCandidateResume(id);
        SessionUserVO current = currentUser(authentication);
        auditLogService.log(current.getId(), current.getDisplayName(), current.getRoleCode(), "RECRUITMENT", "REJECT_RESUME", "RECRUITMENT_CANDIDATE", String.valueOf(id), rejected.getFullName() + " 简历面试拒绝");
        return ApiResponse.success(rejected);
    }

    @PostMapping("/admin/candidates/{id}/reevaluate-resume-llm")
    public ApiResponse<CandidateVO> reevaluateResumeLlm(Authentication authentication,
                                                        @PathVariable Long id) {
        CandidateVO candidate = recruitmentService.reevaluateResumeLlm(id);
        SessionUserVO current = currentUser(authentication);
        auditLogService.log(current.getId(), current.getDisplayName(), current.getRoleCode(), "RECRUITMENT", "REEVALUATE_RESUME_LLM", "RECRUITMENT_CANDIDATE", String.valueOf(id), candidate.getFullName() + " AI简历重评");
        return ApiResponse.success(candidate);
    }

    @DeleteMapping("/admin/candidates/{id}")
    public ApiResponse<Void> deleteCandidate(Authentication authentication,
                                             @PathVariable Long id) {
        recruitmentService.deleteCandidate(id);
        SessionUserVO current = currentUser(authentication);
        auditLogService.log(current.getId(), current.getDisplayName(), current.getRoleCode(), "ADMIN", "DELETE_RECRUITMENT_CANDIDATE", "RECRUITMENT_CANDIDATE", String.valueOf(id), "删除候选人");
        return ApiResponse.success("deleted", null);
    }

    @GetMapping("/jobs")
    public ApiResponse<PageResponse<JobVO>> listOpenJobs(@RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(recruitmentService.listJobs(1, null, null, keyword, PageQuery.of(page, pageSize)));
    }

    @PostMapping("/candidates")
    public ApiResponse<CandidateVO> apply(Authentication authentication,
                                           @Valid @RequestBody CandidateApplyRequest request) {
        return ApiResponse.success(recruitmentService.apply(request, authentication.getName()));
    }

    @GetMapping("/candidates/mine")
    public ApiResponse<PageResponse<CandidateVO>> listMyCandidates(Authentication authentication,
                                                                   @RequestParam(required = false) Integer page,
                                                                   @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(recruitmentService.listMyCandidates(authentication.getName(), PageQuery.of(page, pageSize)));
    }

    @PostMapping("/candidates/{candidateId}/resume")
    public ApiResponse<ResumeFileVO> uploadResume(Authentication authentication,
                                                   @PathVariable Long candidateId,
                                                   @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(recruitmentService.uploadResume(candidateId, authentication.getName(), file));
    }

    @GetMapping("/resumes/{id}")
    public ResponseEntity<Resource> downloadResume(Authentication authentication,
                                                   @PathVariable Long id) {
        SessionUserVO current = currentUser(authentication);
        boolean privileged = List.of("IT_ADMIN", "HR_ADMIN", "HR_USER").contains(current.getRoleCode());
        RecruitmentResumeFile resumeFile = recruitmentService.getResumeFile(id, authentication.getName(), privileged);
        return FileDownloadSupport.buildAttachmentResponse(
                resumeFile.getFilePath(), UploadPaths.RESUME_DIR, resumeFile.getOriginalFileName(),
                resumeFile.getContentType(), "简历文件不可访问");
    }

    @GetMapping("/resumes/{id}/download-url")
    public ApiResponse<DownloadUrlResponse> getResumeDownloadUrl(Authentication authentication,
                                                                   @PathVariable Long id) {
        SessionUserVO current = currentUser(authentication);
        boolean privileged = List.of("IT_ADMIN", "HR_ADMIN", "HR_USER").contains(current.getRoleCode());
        RecruitmentResumeFile resumeFile = recruitmentService.getResumeFile(id, authentication.getName(), privileged);
        return ApiResponse.success(s3ObjectStorageService
                .presignExternalDownloadIfAvailable("resumes/" + resumeFile.getStoredFileName())
                .map(url -> new DownloadUrlResponse(url.toString()))
                .orElseGet(DownloadUrlResponse::localFallback));
    }

    private SessionUserVO currentUser(Authentication authentication) {
        return authService.loadUserByUsername(authentication.getName());
    }
}
