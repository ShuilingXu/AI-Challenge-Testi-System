package com.autohr.modules.hr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("hr_salary_history")
public class SalaryHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private String effectiveMonth;
    private BigDecimal baseSalaryBefore;
    private BigDecimal baseSalaryAfter;
    private String reason;
    private Long operatorUserId;
    private LocalDateTime createdAt;
}
