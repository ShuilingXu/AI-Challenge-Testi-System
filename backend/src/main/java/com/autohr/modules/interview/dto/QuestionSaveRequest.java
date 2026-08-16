package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QuestionSaveRequest {
    private Long id;
    @NotBlank(message = "题目标题必填")
    private String questionTitle;
    private String questionType;
    private String difficulty;
    private String tags;
    @NotBlank(message = "题目内容必填")
    private String content;
    private String referenceAnswer;
    @NotNull(message = "分值必填")
    private Integer score;
    private Integer status;
}
