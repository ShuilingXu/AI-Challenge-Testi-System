package com.autohr.modules.hr.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
@TableName("hr_employee")
public class Employee {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String employeeCode;
    private String fullName;
    private String idCardNo;
    private String mobilePhone;
    private String email;
    private String recruitmentMajor;
    private String positionName;
    private Long managerEmployeeId;
    private Long departmentId;
    private String bankAccountNo;
    private String bankName;
    private LocalDate hireDate;
    private Integer employmentStatus;
    private Long sourceCandidateId;
    private String interviewStageStatus;
    private String sourceChannel;
    private String notes;
    private Long jobId;
    private BigDecimal baseSalary;
    private Integer salaryConfirmed;
    private BigDecimal overtimeRate;
    private String dismissalReason;
    private LocalDate dismissalDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
