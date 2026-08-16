package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiAnswerRequest {
    @NotNull(message = "流程ID必填")
    private Long processId;
    @NotNull(message = "题目ID必填")
    private Long questionId;
    @NotBlank(message = "回答内容必填")
    @Size(max = 5000, message = "回答内容不能超过5000个字符")
    private String answerContent;
}
