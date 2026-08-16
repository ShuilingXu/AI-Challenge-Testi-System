package com.autohr.modules.recruitment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

@Data
public class JobSaveRequest {

    private Long id;
    private String jobCode;

    @NotBlank(message = "招聘岗位名称必填")
    private String jobTitle;

    @NotNull(message = "招聘部门必填")
    private Long departmentId;

    private String departmentName;

    private String workLocation;
    private String jobType;

    @NotNull(message = "招聘人数必填")
    private Integer headcount;

    @NotBlank(message = "任职要求必填")
    private String requirements;

    @NotBlank(message = "岗位职责必填")
    private String responsibilities;

    private String salaryRange;
    private LocalDate publishDate;
    private LocalDate closeDate;
    private Integer status;

    @DecimalMin(value = "0.00", message = "Default overtime rate cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Default overtime rate must fit DECIMAL(12,2)")
    private BigDecimal defaultOvertimeRate;
}
