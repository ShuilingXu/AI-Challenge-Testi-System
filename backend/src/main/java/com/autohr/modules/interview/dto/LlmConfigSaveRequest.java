package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LlmConfigSaveRequest {
    private Long id;
    @NotBlank(message = "配置名称必填")
    @Size(max = 128, message = "配置名称不能超过128个字符")
    private String configName;
    @NotBlank(message = "模型角色必填")
    @Size(max = 32, message = "模型角色不能超过32个字符")
    private String modelRole;
    @NotBlank(message = "OpenAI接口地址必填")
    @Size(max = 255, message = "OpenAI接口地址不能超过255个字符")
    private String baseUrl;
    @Size(max = 255, message = "API Key不能超过255个字符")
    private String apiKey;
    @NotBlank(message = "模型名称必填")
    @Size(max = 128, message = "模型名称不能超过128个字符")
    private String modelName;
    @Size(max = 5000, message = "提示词模板不能超过5000个字符")
    private String promptTemplate;
    @Size(max = 5000, message = "评分规则不能超过5000个字符")
    private String scoringRulePrompt;
    private Integer status;
}
