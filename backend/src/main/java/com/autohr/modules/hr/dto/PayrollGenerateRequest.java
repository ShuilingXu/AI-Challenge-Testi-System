package com.autohr.modules.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PayrollGenerateRequest {
    @Positive private Long employeeId;
    @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "Month must be yyyy-MM") private String salaryMonth;
}
