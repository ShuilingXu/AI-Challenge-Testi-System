package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmissionSaveRequest {
    @NotNull(message = "面试候选人必填")
    private Long interviewCandidateId;
    @NotNull(message = "题目必填")
    private Long questionId;
    @NotBlank(message = "答题内容必填")
    private String answerContent;
}
