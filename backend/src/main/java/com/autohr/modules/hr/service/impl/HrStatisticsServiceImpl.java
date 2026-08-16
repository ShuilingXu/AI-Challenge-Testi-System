package com.autohr.modules.hr.service.impl;

import com.autohr.common.exception.BusinessException;
import com.autohr.modules.hr.dto.HrStatisticsVO;
import com.autohr.modules.hr.service.HrStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class HrStatisticsServiceImpl implements HrStatisticsService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final long CACHE_TTL_MILLIS = 10_000L;
    private final JdbcTemplate jdbc;
    private final ConcurrentHashMap<String, CachedStatistics> cache = new ConcurrentHashMap<>();
    private final Object cacheLock = new Object();

    @Override
    public HrStatisticsVO statistics(String month) {
        YearMonth selected = parseMonth(month);
        String cacheKey = selected.toString();
        CachedStatistics cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) return cached.value();
        synchronized (cacheLock) {
            cached = cache.get(cacheKey);
            if (cached != null && cached.expiresAt() > System.currentTimeMillis()) return cached.value();
            HrStatisticsVO computed = computeStatistics(selected);
            cache.put(cacheKey, new CachedStatistics(computed, System.currentTimeMillis() + CACHE_TTL_MILLIS));
            return computed;
        }
    }

    private HrStatisticsVO computeStatistics(YearMonth selected) {
        YearMonth previousMonth = selected.minusMonths(1);
        HrStatisticsVO result = new HrStatisticsVO();
        result.setSalaryMonth(selected.toString());

        List<Map<String, Object>> employees = jdbc.queryForList(
                "SELECT id,employee_code,full_name,hire_date,base_salary,department_id,job_id "
                        + ",dismissal_date,employment_status FROM hr_employee "
                        + "WHERE employment_status IN (1,3) AND salary_confirmed=1 "
                        + "AND (hire_date IS NULL OR hire_date<=?) "
                        + "AND (dismissal_date IS NULL OR dismissal_date>=?)",
                selected.atEndOfMonth(), selected.atDay(1));
        List<Long> employeeIds = employees.stream().map(employee -> number(employee.get("id"))).toList();
        StatisticsData data = loadStatisticsData(employeeIds, selected, previousMonth);
        Map<Long, BigDecimal> currentGross = grossByEmployee(employees, selected, data);
        Map<Long, BigDecimal> previousGross = grossByEmployee(employees, previousMonth, data);
        Map<Long, BigDecimal> currentBase = baseByEmployee(employees, selected, data);
        Map<Long, BigDecimal> previousBase = baseByEmployee(employees, previousMonth, data);
        BigDecimal currentTotal = sum(currentGross);
        BigDecimal previousTotal = sum(previousGross);
        BigDecimal currentBaseTotal = sum(currentBase);
        BigDecimal previousBaseTotal = sum(previousBase);

        for (Map<String, Object> employee : employees) {
            Long employeeId = number(employee.get("id"));
            BigDecimal gross = currentGross.get(employeeId);
            if (gross == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("employeeId", employeeId);
            item.put("employeeCode", employee.get("employee_code"));
            item.put("employeeName", employee.get("full_name"));
            item.put("grossIncome", gross);
            item.put("monthOverMonth", percentage(currentBase.get(employeeId), previousBase.get(employeeId)));
            LocalDate hireDate = date(employee.get("hire_date"));
            BigDecimal entrySalary = entrySalary(employeeId, employee.get("base_salary"), data.salaryHistories);
            BigDecimal currentBaseSalary = effectiveBaseSalary(employeeId, selected, employee.get("base_salary"), data.salaryHistories);
            item.put("newEmployeeGrowth", hireDate != null && monthsSinceHire(hireDate, selected) >= 0
                    && monthsSinceHire(hireDate, selected) < 3 ? percentage(currentBaseSalary, entrySalary) : null);
            result.getSalary().getEmployees().add(item);
        }
        result.getSalary().setGrossTotal(money(currentTotal));
        result.getSalary().setAverageGross(currentGross.isEmpty() ? ZERO
                : money(currentTotal.divide(BigDecimal.valueOf(currentGross.size()), 8, RoundingMode.HALF_UP)));
        result.getSalary().setMonthOverMonth(percentage(currentBaseTotal, previousBaseTotal));

        populateRecruitment(result, selected, employees, data.salaryHistories);
        populateDismissals(result, selected);
        populateDepartments(result, selected, employees, currentGross);
        return result;
    }

    private record CachedStatistics(HrStatisticsVO value, long expiresAt) {}

    private StatisticsData loadStatisticsData(List<Long> employeeIds, YearMonth selected, YearMonth previous) {
        StatisticsData data = new StatisticsData();
        if (employeeIds.isEmpty()) return data;
        String placeholders = String.join(",", Collections.nCopies(employeeIds.size(), "?"));
        List<Map<String, Object>> historyRows = jdbc.queryForList(
                "SELECT employee_id,effective_month,base_salary_before,base_salary_after "
                        + "FROM hr_salary_history WHERE employee_id IN (" + placeholders + ") "
                        + "ORDER BY employee_id,effective_month DESC,id DESC", employeeIds.toArray());
        for (Map<String, Object> row : historyRows) {
            data.salaryHistories.computeIfAbsent(number(row.get("employee_id")), ignored -> new ArrayList<>()).add(row);
        }
        data.performanceByMonth.put(selected.toString(), loadMonthlyValues(
                "hr_performance_month", "amount", employeeIds, selected.toString(), placeholders));
        data.performanceByMonth.put(previous.toString(), loadMonthlyValues(
                "hr_performance_month", "amount", employeeIds, previous.toString(), placeholders));
        data.overtimeByMonth.put(selected.toString(), loadMonthlyValues(
                "hr_overtime_month", "overtime_pay", employeeIds, selected.toString(), placeholders));
        data.overtimeByMonth.put(previous.toString(), loadMonthlyValues(
                "hr_overtime_month", "overtime_pay", employeeIds, previous.toString(), placeholders));
        return data;
    }

    private Map<Long, BigDecimal> loadMonthlyValues(String table, String column, List<Long> employeeIds,
                                                     String month, String placeholders) {
        List<Object> args = new ArrayList<>(employeeIds);
        args.add(month);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT employee_id," + column + " FROM " + table + " WHERE employee_id IN ("
                        + placeholders + ") AND salary_month=?", args.toArray());
        Map<Long, BigDecimal> values = new HashMap<>();
        for (Map<String, Object> row : rows) {
            values.put(number(row.get("employee_id")), money(row.get(column)));
        }
        return values;
    }

    private static final class StatisticsData {
        private final Map<Long, List<Map<String, Object>>> salaryHistories = new HashMap<>();
        private final Map<String, Map<Long, BigDecimal>> performanceByMonth = new HashMap<>();
        private final Map<String, Map<Long, BigDecimal>> overtimeByMonth = new HashMap<>();
    }

    private Map<Long, BigDecimal> grossByEmployee(List<Map<String, Object>> employees, YearMonth month,
                                                   StatisticsData data) {
        Map<Long, BigDecimal> gross = new HashMap<>();
        Map<Long, BigDecimal> performanceByEmployee = data.performanceByMonth.getOrDefault(month.toString(), Map.of());
        Map<Long, BigDecimal> overtimeByEmployee = data.overtimeByMonth.getOrDefault(month.toString(), Map.of());
        for (Map<String, Object> employee : employees) {
            LocalDate hireDate = date(employee.get("hire_date"));
            LocalDate dismissalDate = date(employee.get("dismissal_date"));
            if (hireDate != null && YearMonth.from(hireDate).isAfter(month)) continue;
            if (dismissalDate != null && YearMonth.from(dismissalDate).isBefore(month)) continue;
            Long employeeId = number(employee.get("id"));
            BigDecimal base = effectiveBaseSalary(employeeId, month, employee.get("base_salary"), data.salaryHistories);
            if (base == null) continue;
            BigDecimal performance = performanceByEmployee.getOrDefault(employeeId, ZERO);
            BigDecimal overtime = overtimeByEmployee.getOrDefault(employeeId, ZERO);
            gross.put(employeeId, money(base.add(performance).add(overtime)));
        }
        return gross;
    }

    private Map<Long, BigDecimal> baseByEmployee(List<Map<String, Object>> employees, YearMonth month,
                                                  StatisticsData data) {
        Map<Long, BigDecimal> bases = new HashMap<>();
        for (Map<String, Object> employee : employees) {
            LocalDate hireDate = date(employee.get("hire_date"));
            LocalDate dismissalDate = date(employee.get("dismissal_date"));
            if (hireDate != null && YearMonth.from(hireDate).isAfter(month)) continue;
            if (dismissalDate != null && YearMonth.from(dismissalDate).isBefore(month)) continue;
            Long employeeId = number(employee.get("id"));
            bases.put(employeeId, effectiveBaseSalary(employeeId, month, employee.get("base_salary"), data.salaryHistories));
        }
        return bases;
    }

    private BigDecimal effectiveBaseSalary(Long employeeId, YearMonth month, Object fallback,
                                           Map<Long, List<Map<String, Object>>> historiesByEmployee) {
        List<Map<String, Object>> history = historiesByEmployee.getOrDefault(employeeId, List.of());
        for (Map<String, Object> row : history) {
            if (row.get("effective_month").toString().compareTo(month.toString()) <= 0) {
                return money(row.get("base_salary_after"));
            }
        }
        if (!history.isEmpty()) {
            Object beforeValue = history.get(history.size() - 1).get("base_salary_before");
            if (beforeValue != null) return money(beforeValue);
        }
        return money(fallback);
    }

    private BigDecimal entrySalary(Long employeeId, Object fallback,
                                   Map<Long, List<Map<String, Object>>> historiesByEmployee) {
        List<Map<String, Object>> history = historiesByEmployee.getOrDefault(employeeId, List.of());
        if (history.isEmpty()) return money(fallback);
        Map<String, Object> first = history.get(history.size() - 1);
        BigDecimal before = money(first.get("base_salary_before"));
        return before.signum() == 0 ? money(first.get("base_salary_after")) : before;
    }

    private void populateRecruitment(HrStatisticsVO result, YearMonth month, List<Map<String, Object>> employees,
                                     Map<Long, List<Map<String, Object>>> historiesByEmployee) {
        LocalDate closeDateCutoff = month.equals(YearMonth.now(BUSINESS_ZONE)) ? LocalDate.now(BUSINESS_ZONE) : month.atEndOfMonth();
        result.getRecruitment().setOpenJobCount(jdbc.queryForObject(
                "SELECT COUNT(*) FROM recruitment_job WHERE status=1 AND (close_date IS NULL OR close_date>=?)",
                Long.class, closeDateCutoff));
        result.getRecruitment().setCandidateCount(jdbc.queryForObject(
                "SELECT COUNT(*) FROM recruitment_candidate WHERE created_at>=? AND created_at<?",
                Long.class, month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay()));
        result.getRecruitment().setInterviewingCount(jdbc.queryForObject(
                "SELECT COUNT(*) FROM interview_process WHERE overall_status='IN_PROGRESS' AND created_at>=? AND created_at<?",
                Long.class, month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay()));
        result.getRecruitment().setPassedCount(jdbc.queryForObject(
                "SELECT COUNT(*) FROM interview_process WHERE overall_status='PASSED' AND created_at>=? AND created_at<?",
                Long.class, month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay()));

        List<Map<String, Object>> jobs = jdbc.queryForList("SELECT id,job_code,job_title FROM recruitment_job ORDER BY id");
        for (Map<String, Object> job : jobs) {
            Long jobId = number(job.get("id"));
            BigDecimal total = ZERO;
            long count = 0;
            for (Map<String, Object> employee : employees) {
                if (!Objects.equals(numberOrNull(employee.get("job_id")), jobId)) continue;
                LocalDate hireDate = date(employee.get("hire_date"));
                // "Average hired salary" is scoped to employees who joined in the selected month.
                if (hireDate == null || !YearMonth.from(hireDate).equals(month)) continue;
                BigDecimal salary = effectiveBaseSalary(number(employee.get("id")), month, employee.get("base_salary"), historiesByEmployee);
                if (salary != null) {
                    total = total.add(salary);
                    count++;
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("jobId", jobId);
            item.put("jobCode", job.get("job_code"));
            item.put("jobTitle", job.get("job_title"));
            item.put("averageBaseSalary", count == 0 ? ZERO
                    : money(total.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP)));
            result.getRecruitment().getJobAverageSalaries().add(item);
        }
    }

    private void populateDismissals(HrStatisticsVO result, YearMonth month) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT e.dismissal_reason,e.salary_confirmed,p.gross_income FROM hr_employee e "
                        + "LEFT JOIN hr_payroll_month p ON p.employee_id=e.id AND p.salary_month=? "
                        + "WHERE e.employment_status=3 AND e.dismissal_date>=? AND e.dismissal_date<=?",
                month.toString(), month.atDay(1), month.atEndOfMonth());
        result.getDismissal().setCount(rows.size());
        BigDecimal total = ZERO;
        long payrollCount = 0;
        Map<String, Long> reasons = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (number(row.get("salary_confirmed")) == 1L && row.get("gross_income") != null) {
                total = total.add(money(row.get("gross_income")));
                payrollCount++;
            }
            reasons.merge(Objects.toString(row.get("dismissal_reason"), "其他"), 1L, Long::sum);
        }
        result.getDismissal().setAverageGross(payrollCount == 0 ? ZERO
                : money(total.divide(BigDecimal.valueOf(payrollCount), 8, RoundingMode.HALF_UP)));
        reasons.forEach((name, count) -> result.getDismissal().getReasons().add(
                new LinkedHashMap<>(Map.of("name", name, "value", count))));
    }

    private void populateDepartments(HrStatisticsVO result, YearMonth month,
                                     List<Map<String, Object>> employees, Map<Long, BigDecimal> currentGross) {
        List<Map<String, Object>> departments = jdbc.queryForList(
                "SELECT id,department_name FROM hr_department "
                        + "WHERE status=1 AND department_name IS NOT NULL AND TRIM(department_name)<>''");
        long employeeTotal = 0;
        long hireTotal = 0;
        long dismissalTotal = 0;
        BigDecimal departmentGrossTotal = ZERO;
        long departmentGrossCount = 0;
        for (Map<String, Object> department : departments) {
            Long departmentId = number(department.get("id"));
            List<Map<String, Object>> departmentEmployees = employees.stream()
                    .filter(employee -> Objects.equals(numberOrNull(employee.get("department_id")), departmentId))
                    .toList();
            if (departmentEmployees.isEmpty()) continue;
            long employeeCount = departmentEmployees.size();
            long hireCount = departmentEmployees.stream().filter(employee -> {
                LocalDate hireDate = date(employee.get("hire_date"));
                return hireDate != null && !hireDate.isBefore(month.atDay(1)) && !hireDate.isAfter(month.atEndOfMonth());
            }).count();
            long dismissalCount = departmentEmployees.stream().filter(employee -> {
                LocalDate dismissalDate = date(employee.get("dismissal_date"));
                return dismissalDate != null && !dismissalDate.isBefore(month.atDay(1)) && !dismissalDate.isAfter(month.atEndOfMonth());
            }).count();
            employeeTotal += employeeCount;
            hireTotal += hireCount;
            dismissalTotal += dismissalCount;
            BigDecimal salaryTotal = ZERO;
            long salaryCount = 0;
            for (Map<String, Object> employee : departmentEmployees) {
                BigDecimal gross = currentGross.get(number(employee.get("id")));
                if (gross != null) {
                    salaryTotal = salaryTotal.add(gross);
                    salaryCount++;
                }
            }
            departmentGrossTotal = departmentGrossTotal.add(salaryTotal);
            departmentGrossCount += salaryCount;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("departmentId", departmentId);
            item.put("departmentName", department.get("department_name"));
            item.put("averageGross", salaryCount == 0 ? ZERO
                    : money(salaryTotal.divide(BigDecimal.valueOf(salaryCount), 8, RoundingMode.HALF_UP)));
            result.getDepartment().getAverageSalaries().add(item);
        }
        int count = result.getDepartment().getAverageSalaries().size();
        result.getDepartment().setAverageEmployeeCount(average(employeeTotal, count));
        result.getDepartment().setAverageHireCount(average(hireTotal, count));
        result.getDepartment().setAverageDismissalCount(average(dismissalTotal, count));
        result.getDepartment().setAverageGrossSalary(departmentGrossCount == 0 ? ZERO
                : money(departmentGrossTotal.divide(BigDecimal.valueOf(departmentGrossCount), 8, RoundingMode.HALF_UP)));
    }

    private BigDecimal sum(Map<Long, BigDecimal> values) {
        return values.values().stream().reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal average(long total, int count) {
        return count == 0 ? ZERO : money(BigDecimal.valueOf(total).divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP));
    }

    private BigDecimal percentage(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) return null;
        return money(current.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous, 8, RoundingMode.HALF_UP));
    }

    private BigDecimal money(Object value) {
        return value == null ? ZERO : new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private Long number(Object value) { return ((Number) value).longValue(); }
    private Long numberOrNull(Object value) { return value == null ? null : number(value); }
    private LocalDate date(Object value) {
        if (value instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        if (value instanceof LocalDate localDate) return localDate;
        return value == null ? null : LocalDate.parse(value.toString());
    }
    private int monthsSinceHire(LocalDate hireDate, YearMonth month) {
        return (month.getYear() - hireDate.getYear()) * 12 + month.getMonthValue() - hireDate.getMonthValue();
    }
    private YearMonth parseMonth(String value) {
        try { return YearMonth.parse(value); }
        catch (Exception ignored) { throw new BusinessException("Month must be yyyy-MM"); }
    }
}
