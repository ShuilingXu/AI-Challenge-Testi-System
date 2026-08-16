package com.autohr.modules.hr.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class HrStatisticsVO {
    private String salaryMonth;
    private Salary salary = new Salary();
    private Recruitment recruitment = new Recruitment();
    private Dismissal dismissal = new Dismissal();
    private Department department = new Department();
    @Data public static class Salary { private BigDecimal grossTotal=BigDecimal.ZERO; private BigDecimal monthOverMonth; private BigDecimal averageGross=BigDecimal.ZERO; private List<Map<String,Object>> employees=new ArrayList<>(); }
    @Data public static class Recruitment { private long openJobCount; private long candidateCount; private long interviewingCount; private long passedCount; private List<Map<String,Object>> jobAverageSalaries=new ArrayList<>(); }
    @Data public static class Dismissal { private long count; private BigDecimal averageGross=BigDecimal.ZERO; private List<Map<String,Object>> reasons=new ArrayList<>(); }
    @Data public static class Department { private BigDecimal averageEmployeeCount=BigDecimal.ZERO; private BigDecimal averageHireCount=BigDecimal.ZERO; private BigDecimal averageDismissalCount=BigDecimal.ZERO; private BigDecimal averageGrossSalary=BigDecimal.ZERO; private List<Map<String,Object>> averageSalaries=new ArrayList<>(); }
}
