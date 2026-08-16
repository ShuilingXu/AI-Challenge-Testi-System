package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BatchSaveRequest {
    private Long id;
    private String batchCode;
    @NotBlank(message = "批次名称必填")
    private String batchName;
    private Long jobId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String description;
    private Integer status;
}
