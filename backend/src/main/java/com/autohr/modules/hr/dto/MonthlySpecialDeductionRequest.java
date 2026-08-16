package com.autohr.modules.hr.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class MonthlySpecialDeductionRequest {
    @NotNull @Positive private Long employeeId;
    @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "Month must be yyyy-MM") private String salaryMonth;
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) private BigDecimal childrenEducation;
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) private BigDecimal continuingEducation;
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) private BigDecimal housingLoanInterest;
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) private BigDecimal housingRent;
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) private BigDecimal elderlySupport;
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) private BigDecimal infantCare;
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) private BigDecimal otherDeduction;
}
