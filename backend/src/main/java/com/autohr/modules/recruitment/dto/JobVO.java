package com.autohr.modules.recruitment.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
public class JobVO {

    private Long id;
    private String jobCode;
    private String jobTitle;
    private Long departmentId;
    private String departmentName;
    private String workLocation;
    private String jobType;
    private Integer headcount;
    private String requirements;
    private String responsibilities;
    private String salaryRange;
    private LocalDate publishDate;
    private LocalDate closeDate;
    private Integer status;
    private BigDecimal defaultOvertimeRate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
