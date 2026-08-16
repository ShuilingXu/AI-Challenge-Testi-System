package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterviewProcessTemplateStageRequest {
    private Long id;
    @NotBlank(message = "阶段名称不能为空")
    private String stageName;
    @NotBlank(message = "阶段类型不能为空")
    private String stageType;
    private Long knowledgeBaseId;
    @Size(max = 2000, message = "每轮知识点计划不能超过2000个字符")
    private String roundKnowledgePoints;
    @NotNull(message = "阶段顺序不能为空")
    private Integer sequenceNo;
}
