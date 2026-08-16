package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignCandidateRequest {
    @NotNull(message = "面试批次必填")
    private Long batchId;
    @NotNull(message = "招聘候选人必填")
    private Long recruitmentCandidateId;
}
