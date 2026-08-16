package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmissionScoreRequest {
    @NotNull(message = "得分必填")
    private Integer score;
    private String reviewerComment;
}
