package com.autohr.modules.hr.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PayrollVO {
    private Long employeeId;
    private String employeeCode;
    private String employeeName;
    private String idCardNo;
    private String salaryMonth;
    private BigDecimal baseSalary;
    private BigDecimal performance;
    private BigDecimal overtimeHours;
    private BigDecimal overtimePay;
    private BigDecimal grossIncome;
    private BigDecimal socialInsuranceTotal;
    private BigDecimal specialDeductionTotal;
    private BigDecimal taxableIncomeMonth;
    private BigDecimal cumulativeIncome;
    private BigDecimal cumulativeDeductionBase;
    private BigDecimal cumulativeSocialInsurance;
    private BigDecimal cumulativeSpecialDeduction;
    private BigDecimal cumulativeTaxableIncome;
    private BigDecimal cumulativeTaxWithheld;
    private BigDecimal currentTaxWithheld;
    private BigDecimal netPay;
    private Integer locked;
    private LocalDateTime calculatedAt;
}
