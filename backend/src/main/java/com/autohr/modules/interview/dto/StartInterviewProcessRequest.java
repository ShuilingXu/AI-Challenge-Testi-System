package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class StartInterviewProcessRequest {
    @NotNull(message = "招聘候选人必填")
    private Long recruitmentCandidateId;
    @NotNull(message = "面试者用户必填")
    private Long intervieweeUserId;
    @NotNull(message = "岗位必填")
    private Long jobId;
    private Long templateId;
    @Min(value = 0, message = "AI通过阈值不能小于0")
    @Max(value = 100, message = "AI通过阈值不能大于100")
    private Integer aiThresholdScore;
    @Min(value = 0, message = "AI追问阈值不能小于0")
    @Max(value = 100, message = "AI追问阈值不能大于100")
    private Integer aiFollowUpThreshold;
    private Integer aiMinQuestionRounds;
    private Integer aiMaxQuestionRounds;
    private Integer antiCheatSwitchLimit;
    private String aiOutputMode;
}
