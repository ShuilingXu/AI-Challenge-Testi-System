package com.autohr.modules.interview.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class InterviewVO {
    private Long id;
    private String batchCode;
    private String batchName;
    private Long jobId;
    private Long jobDepartmentId;
    private String jobDepartmentName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String description;
    private Integer status;

    private String knowledgeBaseName;
    private Long knowledgeBaseId;
    private String roundKnowledgePoints;
    private String techCategory;
    private String jobCategory;
    private String knowledgePoint;
    private String knowledgeContent;
    private Integer weight;

    private String configName;
    private String modelRole;
    private String baseUrl;
    private String apiKeyMasked;
    private String modelName;
    private String promptTemplate;
    private String scoringRulePrompt;

    private Long processId;
    private Long templateId;
    private String templateName;
    private Integer version;
    private Long processStageId;
    private String stageName;
    private String stageType;
    private List<InterviewVO> stages;
    private String currentStage;
    private String stageStatus;
    private String overallStatus;
    private Integer aiThresholdScore;
    private Integer aiFollowUpThreshold;
    private Integer aiAverageScore;
    private Integer aiMinQuestionRounds;
    private Integer aiMaxQuestionRounds;
    private Integer antiCheatSwitchLimit;
    private Integer antiCheatSwitchCount;
    private String aiOutputMode;
    private Integer videoApproved;
    private Integer onsiteApproved;
    private Long intervieweeUserId;
    private Long approvedHrUserId;
    private String approvedHrName;
    private Long approverUserId;
    private String approverName;
    private String processStatusView;
    private String remark;

    private String questionTitle;
    private String questionType;
    private String difficulty;
    private String tags;
    private String content;
    private String referenceAnswer;
    private Integer score;

    private Long batchId;
    private Long recruitmentCandidateId;
    private String candidateName;
    private String mobilePhone;
    private String interviewStatus;
    private Integer totalScore;
    private String interviewerComment;
    private Long interviewCandidateId;
    private Long questionId;
    private String questionContent;
    private String questionStatus;
    private LocalDateTime questionNextRetryAt;
    private String answerContent;
    private String answerStatus;
    private Integer interviewerScore;
    private Integer scorerScore;
    private Integer averageScore;
    private Integer sequenceNo;
    private String reviewerComment;

    private String videoSerialNo;
    private String videoJoinLink;
    private LocalDateTime intervieweeJoinTime;
    private LocalDateTime hrJoinTime;
    private LocalDateTime recordingEndRequestedAt;
    private String recordingPath;
    private String recordingFileName;
    private String aiRecordingPath;
    private String aiRecordingFileName;
    private String sessionStatus;
    private String audioPath;
    private String audioFileName;
    private String transcriptText;
    private String summaryText;
    private String summaryStatus;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
