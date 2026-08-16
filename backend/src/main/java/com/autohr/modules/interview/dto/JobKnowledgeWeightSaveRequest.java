package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class JobKnowledgeWeightSaveRequest {
    private Long id;
    @NotNull(message = "岗位必填")
    private Long jobId;
    @NotNull(message = "知识库必填")
    private Long knowledgeBaseId;
    @NotNull(message = "权重必填")
    @Min(value = 1, message = "权重不能小于1")
    @Max(value = 1000, message = "权重不能超过1000")
    private Integer weight;
}
