package com.autohr.modules.hr.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class HrDashboardVO {

    private Long departmentCount;
    private Long employeeCount;
    private Long activeEmployeeCount;
    private Long pendingOnboardingCount;
    private Long resignedCount;
    private Long recruitmentBindingCount;
    private Long performanceBindingCount;
    private Long openJobCount;
    private Long currentMonthHireCount;
    private Long currentMonthDismissalCount;
    private BigDecimal averageGrossSalary;
    private HrStatisticsVO statistics;
}
