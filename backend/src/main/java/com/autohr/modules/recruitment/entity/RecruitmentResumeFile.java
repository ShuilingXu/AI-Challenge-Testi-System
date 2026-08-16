package com.autohr.modules.recruitment.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recruitment_resume_file")
public class RecruitmentResumeFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long candidateId;
    private String originalFileName;
    private String storedFileName;
    private String filePath;
    private String contentType;
    private Long fileSize;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
