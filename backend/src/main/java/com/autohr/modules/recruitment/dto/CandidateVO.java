package com.autohr.modules.recruitment.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CandidateVO {

    private Long id;
    private Long jobId;
    private String jobTitle;
    private String fullName;
    private String mobilePhone;
    private String email;
    private String idCardNo;
    private String major;
    private String educationLevel;
    private String graduationSchool;
    private Integer yearsOfExperience;
    private String expectedSalary;
    private String selfIntroduction;
    private String applicationStatus;
    private String interviewStageStatus;
    private Long intervieweeUserId;
    private Long interviewProcessId;
    private Long resumeFileId;
    private String resumeFileName;
    private Integer resumeLlmScore;
    private String resumeLlmComment;
    private String resumeLlmStatus;
    private LocalDateTime resumeLlmEvaluatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
