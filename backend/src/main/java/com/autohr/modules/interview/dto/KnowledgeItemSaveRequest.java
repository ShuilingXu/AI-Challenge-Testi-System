package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KnowledgeItemSaveRequest {
    private Long id;
    @NotNull(message = "知识库必填")
    private Long knowledgeBaseId;
    @NotBlank(message = "知识点必填")
    @Size(max = 255, message = "知识点不能超过255个字符")
    private String knowledgePoint;
    @NotBlank(message = "知识内容必填")
    @Size(max = 5000, message = "知识内容不能超过5000个字符")
    private String knowledgeContent;
    private Integer status;
}
