package com.autohr.modules.interview.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_llm_config")
public class InterviewLlmConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String configName;
    private String modelRole;
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private String promptTemplate;
    private String scoringRulePrompt;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
