package com.autohr.modules.recruitment.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ResumeFileVO {

    private Long id;
    private Long candidateId;
    private String originalFileName;
    private String storedFileName;
    private String filePath;
    private String contentType;
    private Long fileSize;
    private LocalDateTime createdAt;
}
