package com.autohr.modules.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonAlias;

@Data
public class EmployeeSaveRequest {

    private Long id;
    private String employeeCode;

    @NotBlank(message = "姓名必填")
    private String fullName;

    @NotBlank(message = "身份证号必填")
    private String idCardNo;

    @NotBlank(message = "手机号必填")
    private String mobilePhone;

    private String email;

    @NotBlank(message = "招聘专业必填")
    private String recruitmentMajor;

    private String positionName;

    @NotNull(message = "Job is required")
    @Positive(message = "Job id must be positive")
    private Long jobId;

    @NotNull(message = "Base salary is required")
    @DecimalMin(value = "0.00", message = "Base salary cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Base salary must fit DECIMAL(12,2)")
    private BigDecimal baseSalary;

    @DecimalMin(value = "0.00", message = "Overtime rate cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Overtime rate must fit DECIMAL(12,2)")
    private BigDecimal overtimeRate;

    private Long managerEmployeeId;

    @NotNull(message = "直属部门必填")
    @Positive(message = "Department id must be positive")
    private Long departmentId;

    @NotBlank(message = "银行卡号必填")
    private String bankAccountNo;

    @NotBlank(message = "开户银行必填")
    private String bankName;

    private LocalDate hireDate;
    private Integer employmentStatus;
    private String sourceChannel;
    private String notes;

    @Pattern(regexp = "试用期不合格|违纪辞退|组织调整|协商解除|其他", message = "Invalid dismissal reason")
    private String dismissalReason;
    private LocalDate dismissalDate;
    @Size(max = 500, message = "Salary change reason cannot exceed 500 characters")
    private String salaryChangeReason;

    /** Optional month in which the salary change becomes effective (yyyy-MM). */
    @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "Effective month must be yyyy-MM")
    @JsonAlias("effective_month")
    private String effectiveMonth;

    @JsonIgnore
    @AssertTrue(message = "New employee base salary must be positive")
    public boolean isBaseSalaryValid() {
        if (baseSalary == null) {
            return true;
        }
        return id == null ? baseSalary.signum() > 0 : baseSalary.signum() >= 0;
    }
}
