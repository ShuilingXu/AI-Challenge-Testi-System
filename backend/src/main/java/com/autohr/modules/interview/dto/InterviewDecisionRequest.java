package com.autohr.modules.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
public class InterviewDecisionRequest {
    @NotNull(message = "通过标记必填")
    @Min(value = 0, message = "审批结果必须为0或1")
    @Max(value = 1, message = "审批结果必须为0或1")
    private Integer approved;
    private String approverName;
    private Long approverUserId;
    @JsonIgnore
    private String approverRoleCode;
    private String comment;
    private Long departmentId;
    private Long jobId;
    @DecimalMin(value = "0.01", message = "Base salary must be positive")
    @Digits(integer = 10, fraction = 2, message = "Base salary must fit DECIMAL(12,2)")
    private BigDecimal baseSalary;
}
