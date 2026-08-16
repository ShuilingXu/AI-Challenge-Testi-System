package com.autohr.modules.interview.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_candidate")
public class InterviewCandidate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;
    private Long recruitmentCandidateId;
    private String candidateName;
    private String mobilePhone;
    private String interviewStatus;
    private Integer totalScore;
    private String interviewerComment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
