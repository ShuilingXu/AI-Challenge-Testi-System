package com.autohr.modules.interview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class InterviewProcessTemplateSaveRequest {
    private Long id;
    private Integer version;
    @NotBlank(message = "模板名称不能为空")
    private String templateName;
    private String description;
    private Integer status;
    @Valid
    @NotEmpty(message = "请至少添加一个面试阶段")
    private List<InterviewProcessTemplateStageRequest> stages;
}
