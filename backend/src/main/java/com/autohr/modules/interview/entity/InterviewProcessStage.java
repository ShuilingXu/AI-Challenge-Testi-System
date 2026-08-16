package com.autohr.modules.interview.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_process_stage")
public class InterviewProcessStage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long processId;
    private Long templateStageId;
    private String stageName;
    private String stageType;
    private Long knowledgeBaseId;
    private String roundKnowledgePoints;
    private Integer sequenceNo;
    private String stageStatus;
    private Integer approved;
    private Long approvedHrUserId;
    private String approvedHrName;
    private String aiRecordingPath;
    private String aiRecordingFileName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
