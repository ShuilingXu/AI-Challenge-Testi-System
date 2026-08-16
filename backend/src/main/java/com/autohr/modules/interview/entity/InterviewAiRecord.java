package com.autohr.modules.interview.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_ai_record")
public class InterviewAiRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long processId;
    private Long processStageId;
    private Long stageScopeId;
    private Long knowledgeBaseId;
    private String knowledgePoint;
    private String questionContent;
    private String questionStatus;
    private Integer questionGenerationAttempts;
    private String questionGenerationToken;
    private LocalDateTime questionLeaseExpiresAt;
    private LocalDateTime questionNextRetryAt;
    private String questionGenerationError;
    private Long previousRecordId;
    private String suggestedNextQuestion;
    private String answerContent;
    private String answerStatus;
    private String answerProcessingToken;
    private LocalDateTime answerLeaseExpiresAt;
    private Integer answerProcessingAttempts;
    private String answerError;
    private Integer interviewerScore;
    private Integer scorerScore;
    private Integer averageScore;
    private String interviewerComment;
    private Integer sequenceNo;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
