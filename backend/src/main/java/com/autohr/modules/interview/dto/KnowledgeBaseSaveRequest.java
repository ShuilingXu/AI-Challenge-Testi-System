package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KnowledgeBaseSaveRequest {
    private Long id;
    @NotBlank(message = "知识库名称必填")
    @Size(max = 128, message = "知识库名称不能超过128个字符")
    private String knowledgeBaseName;
    @Size(max = 128, message = "技术分类不能超过128个字符")
    private String techCategory;
    @Size(max = 128, message = "岗位分类不能超过128个字符")
    private String jobCategory;
    private Integer status;
}
