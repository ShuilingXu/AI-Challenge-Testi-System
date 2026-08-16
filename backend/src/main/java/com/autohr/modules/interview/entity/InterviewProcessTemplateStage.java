package com.autohr.modules.interview.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_process_template_stage")
public class InterviewProcessTemplateStage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private String stageName;
    private String stageType;
    private Long knowledgeBaseId;
    private String roundKnowledgePoints;
    private Integer sequenceNo;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
