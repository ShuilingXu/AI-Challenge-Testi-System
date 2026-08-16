package com.autohr.modules.recruitment.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recruitment_candidate")
public class RecruitmentCandidate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;
    private String fullName;
    private String mobilePhone;
    private String email;
    private String idCardNo;
    private String major;
    private String educationLevel;
    private String graduationSchool;
    private Integer yearsOfExperience;
    private String expectedSalary;
    private String selfIntroduction;
    private String applicationStatus;
    private String interviewStageStatus;
    private Long intervieweeUserId;
    private Long interviewProcessId;
    private Long resumeFileId;
    private Integer resumeLlmScore;
    private String resumeLlmComment;
    private String resumeLlmStatus;
    private LocalDateTime resumeLlmEvaluatedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
