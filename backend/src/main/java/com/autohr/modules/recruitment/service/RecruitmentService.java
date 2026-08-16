package com.autohr.modules.recruitment.service;

import com.autohr.common.api.PageQuery;
import com.autohr.common.api.PageResponse;
import com.autohr.modules.recruitment.dto.CandidateApplyRequest;
import com.autohr.modules.recruitment.dto.CandidateVO;
import com.autohr.modules.recruitment.dto.JobSaveRequest;
import com.autohr.modules.recruitment.dto.JobVO;
import com.autohr.modules.recruitment.dto.ResumeFileVO;
import com.autohr.modules.recruitment.entity.RecruitmentResumeFile;
import org.springframework.web.multipart.MultipartFile;

public interface RecruitmentService {

    JobVO saveJob(JobSaveRequest request);

    PageResponse<JobVO> listJobs(Integer status, String departmentName, String jobType, String keyword, PageQuery pageQuery);

    void deleteJob(Long id);

    CandidateVO apply(CandidateApplyRequest request, String intervieweeUsername);

    PageResponse<CandidateVO> listCandidates(Long jobId, String status, String interviewStageStatus, String keyword,
                                              PageQuery pageQuery);

    PageResponse<CandidateVO> listMyCandidates(String intervieweeUsername, PageQuery pageQuery);

    CandidateVO getCandidate(Long id);

    CandidateVO rejectCandidateResume(Long id);

    CandidateVO reevaluateResumeLlm(Long id);

    void deleteCandidate(Long id);

    ResumeFileVO uploadResume(Long candidateId, String intervieweeUsername, MultipartFile file);

    RecruitmentResumeFile getResumeFile(Long id, String username, boolean privileged);
}
