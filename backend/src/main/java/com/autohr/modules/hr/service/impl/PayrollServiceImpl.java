package com.autohr.modules.hr.service.impl;

import com.autohr.common.exception.BusinessException;
import com.autohr.common.security.SensitiveDataMasker;
import com.autohr.config.database.ActiveDatabase;
import com.autohr.config.database.DatabaseType;
import com.autohr.modules.hr.dto.*;
import com.autohr.modules.hr.service.PayrollService;
import com.autohr.modules.hr.service.PayrollCalculations;
import com.autohr.modules.hr.service.PayrollMutationGuard;
import com.autohr.modules.hr.service.PayrollExportTemplate;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STCellType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PayrollServiceImpl implements PayrollService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal MONTHLY_DEDUCTION = new BigDecimal("5000.00");
    private static final String LOCKED_FUTURE_PAYROLL_MESSAGE =
            "A later payroll is locked; unlock subsequent months before changing this payroll period";
    private final JdbcTemplate jdbc;
    private final Validator validator;
    private ActiveDatabase activeDatabase;

    @Autowired
    void configureDatabase(ActiveDatabase activeDatabase) {
        this.activeDatabase = activeDatabase;
    }

    @Override @Transactional
    public void savePerformance(MonthlyPerformanceRequest request, Long operatorUserId) {
        assertInputWritable(request.getEmployeeId(), request.getSalaryMonth());
        ensureEmployee(request.getEmployeeId());
        upsertMonthlyInput("hr_performance_month",
                "employee_id,salary_month,amount,note,operator_user_id",
                "amount,note,operator_user_id",
                request.getEmployeeId(), request.getSalaryMonth(), money(request.getAmount()), request.getNote(), operatorUserId);
    }

    @Override @Transactional
    public void saveOvertime(MonthlyOvertimeRequest request, Long operatorUserId) {
        assertInputWritable(request.getEmployeeId(), request.getSalaryMonth());
        Map<String, Object> employee = ensureEmployee(request.getEmployeeId());
        BigDecimal rate = value(employee.get("overtime_rate"));
        if (employee.get("overtime_rate") == null) rate = value(employee.get("default_overtime_rate"));
        rate = PayrollCalculations.resolveOvertimeRate(employee.get("overtime_rate") == null ? null : value(employee.get("overtime_rate")), rate);
        BigDecimal hours = money(request.getOvertimeHours());
        BigDecimal pay = PayrollCalculations.overtimePay(hours, rate);
        upsertMonthlyInput("hr_overtime_month",
                "employee_id,salary_month,overtime_hours,unit_rate,overtime_pay,note,operator_user_id",
                "overtime_hours,unit_rate,overtime_pay,note,operator_user_id",
                request.getEmployeeId(), request.getSalaryMonth(), hours, rate, pay, request.getNote(), operatorUserId);
    }

    @Override @Transactional
    public void saveSocialInsurance(MonthlySocialInsuranceRequest request) {
        assertInputWritable(request.getEmployeeId(), request.getSalaryMonth()); ensureEmployee(request.getEmployeeId());
        upsertMonthlyInput("hr_social_insurance_month",
                "employee_id,salary_month,pension,medical,unemployment,housing_fund",
                "pension,medical,unemployment,housing_fund",
                request.getEmployeeId(), request.getSalaryMonth(), money(request.getPension()), money(request.getMedical()),
                money(request.getUnemployment()), money(request.getHousingFund()));
    }

    @Override @Transactional
    public void saveSpecialDeduction(MonthlySpecialDeductionRequest request) {
        assertInputWritable(request.getEmployeeId(), request.getSalaryMonth()); ensureEmployee(request.getEmployeeId());
        upsertMonthlyInput("hr_special_deduction_month",
                "employee_id,salary_month,children_education,continuing_education,housing_loan_interest,housing_rent,elderly_support,infant_care,other_deduction",
                "children_education,continuing_education,housing_loan_interest,housing_rent,elderly_support,infant_care,other_deduction",
                request.getEmployeeId(), request.getSalaryMonth(), money(request.getChildrenEducation()), money(request.getContinuingEducation()),
                money(request.getHousingLoanInterest()), money(request.getHousingRent()), money(request.getElderlySupport()),
                money(request.getInfantCare()), money(request.getOtherDeduction()));
    }

    @Override @Transactional
    public List<PayrollVO> generate(PayrollGenerateRequest request) {
        YearMonth target = parseMonth(request.getSalaryMonth());
        List<Map<String, Object>> employees = request.getEmployeeId() == null
                ? jdbc.queryForList("SELECT e.*, j.default_overtime_rate FROM hr_employee e LEFT JOIN recruitment_job j ON e.job_id=j.id WHERE e.employment_status IN (1,3) AND e.salary_confirmed=1 ORDER BY e.id")
                : List.of(ensureEmployee(request.getEmployeeId()));
        List<PayrollVO> result = new ArrayList<>();
        for (Map<String, Object> employee : employees) {
            if (integer(employee.get("salary_confirmed")) != 1) throw new BusinessException("Employee salary has not been confirmed");
            Long employeeId = number(employee.get("id"));
            lockEmployeePayroll(employeeId);
            LocalDate hireDate = localDate(employee.get("hire_date"));
            LocalDate dismissalDate = localDate(employee.get("dismissal_date"));
            int employmentStatus = integer(employee.get("employment_status"));
            boolean supportedStatus = employmentStatus == 1 || employmentStatus == 3;
            boolean beforeDismissal = dismissalDate == null || !target.isAfter(YearMonth.from(dismissalDate));
            boolean eligible = supportedStatus && beforeDismissal
                    && (hireDate == null || !YearMonth.from(hireDate).isAfter(target));
            if (!eligible) {
                if (request.getEmployeeId() != null) {
                    throw new BusinessException("Employee is not eligible for payroll in the selected month");
                }
                continue;
            }
            if (isLocked(employeeId, request.getSalaryMonth())) {
                if (request.getEmployeeId() != null) {
                    throw new BusinessException("Payroll is locked for this month");
                }
                continue;
            }
            assertNoLockedFuturePayroll(employeeId, target.toString());
            PayrollVO payroll = calculate(employee, target);
            upsertPayroll(payroll);
            result.add(payroll);
        }
        return result;
    }

    @Override
    public List<PayrollVO> listPayroll(String salaryMonth, Long employeeId) {
        return queryPayroll(salaryMonth, employeeId, true);
    }

    private List<PayrollVO> queryPayroll(String salaryMonth, Long employeeId, boolean maskSensitiveData) {
        parseMonth(salaryMonth);
        String sql = "SELECT p.*,e.employee_code,e.full_name,e.id_card_no FROM hr_payroll_month p JOIN hr_employee e ON e.id=p.employee_id WHERE p.salary_month=?";
        List<Object> args = new ArrayList<>(List.of(salaryMonth));
        if (employeeId != null) { sql += " AND p.employee_id=?"; args.add(employeeId); }
        sql += " ORDER BY e.employee_code";
        return jdbc.queryForList(sql, args.toArray()).stream()
                .map(row -> toPayroll(row, maskSensitiveData))
                .toList();
    }

    @Override @Transactional
    public void setLocked(Long employeeId, String salaryMonth, boolean locked) {
        parseMonth(salaryMonth);
        lockEmployeePayroll(employeeId);
        int changed = jdbc.update("UPDATE hr_payroll_month SET locked=? WHERE employee_id=? AND salary_month=?", locked ? 1 : 0, employeeId, salaryMonth);
        if (changed == 0) throw new BusinessException("Payroll does not exist");
    }

    @Override @Transactional
    public void deletePayroll(Long employeeId, String salaryMonth) {
        parseMonth(salaryMonth);
        lockEmployeePayroll(employeeId);
        assertWritable(employeeId, salaryMonth);
        assertNoLockedFuturePayroll(employeeId, salaryMonth);
        int changed = jdbc.update("DELETE FROM hr_payroll_month WHERE employee_id=? AND salary_month=?", employeeId, salaryMonth);
        if (changed == 0) throw new BusinessException("Payroll does not exist");
    }

    private PayrollVO calculate(Map<String, Object> employee, YearMonth target) {
        Long id = number(employee.get("id"));
        LocalDate hireDate = localDate(employee.get("hire_date"));
        YearMonth start = YearMonth.of(target.getYear(), 1);
        if (hireDate != null && YearMonth.from(hireDate).isAfter(start)) start = YearMonth.from(hireDate);
        BigDecimal income = ZERO, social = ZERO, special = ZERO;
        PayrollVO targetPayroll = null;
        for (YearMonth month = start; !month.isAfter(target); month = month.plusMonths(1)) {
            MonthAmounts amounts = amounts(id, month.toString(), employee);
            income = money(income.add(amounts.gross));
            social = money(social.add(amounts.social));
            special = money(special.add(amounts.special));
            int months = (month.getMonthValue() - start.getMonthValue()) + 1;
            BigDecimal deductionBase = MONTHLY_DEDUCTION.multiply(BigDecimal.valueOf(months));
            BigDecimal taxable = maxZero(income.subtract(deductionBase).subtract(social).subtract(special));
            if (month.equals(target)) {
                BigDecimal cumulativeTaxDue = PayrollCalculations.cumulativeTax(taxable);
                BigDecimal priorWithheld = scalar(
                        "SELECT COALESCE(SUM(current_tax_withheld),0) FROM hr_payroll_month WHERE employee_id=? AND salary_month>=? AND salary_month<?",
                        id, start.toString(), target.toString());
                BigDecimal current = maxZero(cumulativeTaxDue.subtract(priorWithheld));
                PayrollVO vo = new PayrollVO();
                vo.setEmployeeId(id); vo.setEmployeeCode(text(employee.get("employee_code"))); vo.setEmployeeName(text(employee.get("full_name"))); vo.setIdCardNo(SensitiveDataMasker.maskIdentityOrAccount(text(employee.get("id_card_no")))); vo.setSalaryMonth(month.toString());
                vo.setBaseSalary(amounts.base); vo.setPerformance(amounts.performance); vo.setOvertimeHours(amounts.hours); vo.setOvertimePay(amounts.overtimePay); vo.setGrossIncome(amounts.gross);
                vo.setSocialInsuranceTotal(amounts.social); vo.setSpecialDeductionTotal(amounts.special); vo.setTaxableIncomeMonth(maxZero(amounts.gross.subtract(MONTHLY_DEDUCTION).subtract(amounts.social).subtract(amounts.special)));
                vo.setCumulativeIncome(income); vo.setCumulativeDeductionBase(money(deductionBase)); vo.setCumulativeSocialInsurance(social); vo.setCumulativeSpecialDeduction(special); vo.setCumulativeTaxableIncome(taxable); vo.setCumulativeTaxWithheld(cumulativeTaxDue); vo.setCurrentTaxWithheld(current); vo.setNetPay(money(amounts.gross.subtract(amounts.social).subtract(current))); vo.setLocked(0); vo.setCalculatedAt(LocalDateTime.now());
                targetPayroll = vo;
            }
        }
        return targetPayroll;
    }

    private MonthAmounts amounts(Long employeeId, String month, Map<String, Object> employee) {
        BigDecimal base = baseForMonth(employeeId, month, value(employee.get("base_salary")));
        BigDecimal performance = scalar("SELECT amount FROM hr_performance_month WHERE employee_id=? AND salary_month=?", employeeId, month);
        List<Map<String,Object>> overtimeRows = jdbc.queryForList("SELECT overtime_hours,overtime_pay FROM hr_overtime_month WHERE employee_id=? AND salary_month=?", employeeId, month);
        BigDecimal hours = overtimeRows.isEmpty() ? ZERO : value(overtimeRows.get(0).get("overtime_hours"));
        BigDecimal overtimePay = overtimeRows.isEmpty() ? ZERO : value(overtimeRows.get(0).get("overtime_pay"));
        List<Map<String,Object>> socialRows = jdbc.queryForList("SELECT pension,medical,unemployment,housing_fund FROM hr_social_insurance_month WHERE employee_id=? AND salary_month=?", employeeId, month);
        BigDecimal social = socialRows.isEmpty() ? ZERO : sum(socialRows.get(0), "pension", "medical", "unemployment", "housing_fund");
        List<Map<String,Object>> specialRows = jdbc.queryForList("SELECT children_education,continuing_education,housing_loan_interest,housing_rent,elderly_support,infant_care,other_deduction FROM hr_special_deduction_month WHERE employee_id=? AND salary_month=?", employeeId, month);
        BigDecimal special = specialRows.isEmpty() ? ZERO : sum(specialRows.get(0), "children_education", "continuing_education", "housing_loan_interest", "housing_rent", "elderly_support", "infant_care", "other_deduction");
        return new MonthAmounts(base, performance, hours, overtimePay, money(base.add(performance).add(overtimePay)), social, special);
    }

    void upsertPayroll(PayrollVO p) {
        Object[] args = {p.getEmployeeId(),p.getSalaryMonth(),p.getBaseSalary(),p.getPerformance(),p.getOvertimeHours(),
                p.getOvertimePay(),p.getGrossIncome(),p.getSocialInsuranceTotal(),p.getSpecialDeductionTotal(),
                p.getTaxableIncomeMonth(),p.getCumulativeIncome(),p.getCumulativeDeductionBase(),
                p.getCumulativeSocialInsurance(),p.getCumulativeSpecialDeduction(),p.getCumulativeTaxableIncome(),
                p.getCumulativeTaxWithheld(),p.getCurrentTaxWithheld(),p.getNetPay()};
        com.autohr.config.database.DatabaseType databaseType = activeDatabase == null
                ? com.autohr.config.database.DatabaseType.SQLITE : activeDatabase.type();
        String sql = payrollUpsertSql(databaseType);
        int affected = jdbc.update(sql, args);
        if ((affected == 0 || databaseType == DatabaseType.MYSQL)
                && isLocked(p.getEmployeeId(), p.getSalaryMonth())) {
            throw new BusinessException("Payroll is locked for this month");
        }
    }

    static String payrollUpsertSql(DatabaseType databaseType) {
        String insert = "INSERT INTO hr_payroll_month (employee_id,salary_month,base_salary,performance,overtime_hours,overtime_pay,gross_income,social_insurance_total,special_deduction_total,taxable_income_month,cumulative_income,cumulative_deduction_base,cumulative_social_insurance,cumulative_special_deduction,cumulative_taxable_income,cumulative_tax_withheld,current_tax_withheld,net_pay,locked) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0) ";
        String[] columns = {"base_salary", "performance", "overtime_hours", "overtime_pay", "gross_income",
                "social_insurance_total", "special_deduction_total", "taxable_income_month", "cumulative_income",
                "cumulative_deduction_base", "cumulative_social_insurance", "cumulative_special_deduction",
                "cumulative_taxable_income", "cumulative_tax_withheld", "current_tax_withheld", "net_pay"};
        if (databaseType == DatabaseType.MYSQL) {
            String assignments = Arrays.stream(columns)
                    .map(column -> column + "=IF(locked=0,VALUES(" + column + ")," + column + ")")
                    .collect(java.util.stream.Collectors.joining(","));
            return insert + "ON DUPLICATE KEY UPDATE " + assignments
                    + ",calculated_at=IF(locked=0,CURRENT_TIMESTAMP,calculated_at)";
        }
        String assignments = Arrays.stream(columns)
                .map(column -> column + "=EXCLUDED." + column)
                .collect(java.util.stream.Collectors.joining(","));
        return insert + "ON CONFLICT (employee_id,salary_month) DO UPDATE SET " + assignments
                + ",calculated_at=CURRENT_TIMESTAMP WHERE hr_payroll_month.locked=0";
    }

    private void assertWritable(Long employeeId, String month) { PayrollMutationGuard.requireWritable(isLocked(employeeId, month)); }
    private void assertInputWritable(Long employeeId, String month) {
        parseMonth(month);
        lockEmployeePayroll(employeeId);
        assertWritable(employeeId, month);
        assertNoLockedFuturePayroll(employeeId, month);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM hr_payroll_month WHERE employee_id=? AND salary_month=?",
                Long.class, employeeId, month) > 0) {
            throw new BusinessException("Payroll has already been generated for this month; delete or regenerate it before changing inputs");
        }
    }
    private void assertNoLockedFuturePayroll(Long employeeId, String month) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_payroll_month WHERE employee_id=? AND salary_month>? AND locked=1",
                Long.class, employeeId, month);
        if (count != null && count > 0) {
            throw new BusinessException(LOCKED_FUTURE_PAYROLL_MESSAGE);
        }
    }
    private boolean isLocked(Long employeeId, String month) { return jdbc.queryForObject("SELECT COUNT(*) FROM hr_payroll_month WHERE employee_id=? AND salary_month=? AND locked=1", Long.class, employeeId, month) > 0; }
    private void lockEmployeePayroll(Long employeeId) {
        DatabaseType databaseType = activeDatabase == null ? DatabaseType.SQLITE : activeDatabase.type();
        if (databaseType == DatabaseType.SQLITE) {
            return;
        }
        List<Long> ids = jdbc.query("SELECT id FROM hr_employee WHERE id=? FOR UPDATE",
                (resultSet, rowNum) -> resultSet.getLong(1), employeeId);
        if (ids.isEmpty()) {
            throw new BusinessException("Employee does not exist: " + employeeId);
        }
    }
    private Map<String,Object> ensureEmployee(Long id) { List<Map<String,Object>> rows = jdbc.queryForList("SELECT e.*,j.default_overtime_rate FROM hr_employee e LEFT JOIN recruitment_job j ON e.job_id=j.id WHERE e.id=?", id); if (rows.isEmpty()) throw new BusinessException("Employee does not exist: " + id); return rows.get(0); }
    private void upsertMonthlyInput(String table, String insertColumns, String updateColumns, Object... values) {
        String[] columns = insertColumns.split(",");
        String[] updates = updateColumns.split(",");
        String placeholders = String.join(",", Collections.nCopies(columns.length, "?"));
        DatabaseType databaseType = activeDatabase == null ? DatabaseType.SQLITE : activeDatabase.type();
        String sql;
        if (databaseType == DatabaseType.MYSQL) {
            String duplicateAssignments = Arrays.stream(updates)
                    .map(column -> column + "=VALUES(" + column + ")")
                    .collect(java.util.stream.Collectors.joining(","));
            sql = "INSERT INTO " + table + " (" + insertColumns + ") VALUES (" + placeholders
                    + ") ON DUPLICATE KEY UPDATE " + duplicateAssignments + ",updated_at=CURRENT_TIMESTAMP";
        } else {
            String excludedAssignments = Arrays.stream(updates)
                    .map(column -> column + "=excluded." + column)
                    .collect(java.util.stream.Collectors.joining(","));
            sql = "INSERT INTO " + table + " (" + insertColumns + ") VALUES (" + placeholders
                    + ") ON CONFLICT (employee_id,salary_month) DO UPDATE SET " + excludedAssignments
                    + ",updated_at=CURRENT_TIMESTAMP";
        }
        jdbc.update(sql, values);
    }

    private BigDecimal baseForMonth(Long employeeId, String month, BigDecimal fallback) {
        List<Map<String,Object>> rows = jdbc.queryForList(
                "SELECT effective_month,base_salary_before,base_salary_after FROM hr_salary_history "
                        + "WHERE employee_id=? ORDER BY effective_month DESC,id DESC", employeeId);
        for (Map<String, Object> row : rows) {
            if (text(row.get("effective_month")).compareTo(month) <= 0) {
                return value(row.get("base_salary_after"));
            }
        }
        if (!rows.isEmpty()) {
            // Backdated payroll before the first adjustment uses the prior salary.
            Object beforeValue = rows.get(rows.size() - 1).get("base_salary_before");
            if (beforeValue != null) return value(beforeValue);
        }
        return money(fallback);
    }
    private BigDecimal scalar(String sql, Object... args) { List<Map<String,Object>> rows = jdbc.queryForList(sql, args); return rows.isEmpty() ? ZERO : value(rows.get(0).values().iterator().next()); }
    private BigDecimal sum(Map<String,Object> map, String... keys) { BigDecimal total=ZERO; for(String key:keys) total=total.add(value(map.get(key))); return money(total); }
    private PayrollVO toPayroll(Map<String,Object> r, boolean maskSensitiveData) { PayrollVO p=new PayrollVO(); p.setEmployeeId(number(r.get("employee_id")));p.setEmployeeCode(text(r.get("employee_code")));p.setEmployeeName(text(r.get("full_name")));String idCardNo=text(r.get("id_card_no"));p.setIdCardNo(maskSensitiveData ? SensitiveDataMasker.maskIdentityOrAccount(idCardNo) : idCardNo);p.setSalaryMonth(text(r.get("salary_month")));p.setBaseSalary(value(r.get("base_salary")));p.setPerformance(value(r.get("performance")));p.setOvertimeHours(value(r.get("overtime_hours")));p.setOvertimePay(value(r.get("overtime_pay")));p.setGrossIncome(value(r.get("gross_income")));p.setSocialInsuranceTotal(value(r.get("social_insurance_total")));p.setSpecialDeductionTotal(value(r.get("special_deduction_total")));p.setTaxableIncomeMonth(value(r.get("taxable_income_month")));p.setCumulativeIncome(value(r.get("cumulative_income")));p.setCumulativeDeductionBase(value(r.get("cumulative_deduction_base")));p.setCumulativeSocialInsurance(value(r.get("cumulative_social_insurance")));p.setCumulativeSpecialDeduction(value(r.get("cumulative_special_deduction")));p.setCumulativeTaxableIncome(value(r.get("cumulative_taxable_income")));p.setCumulativeTaxWithheld(value(r.get("cumulative_tax_withheld")));p.setCurrentTaxWithheld(value(r.get("current_tax_withheld")));p.setNetPay(value(r.get("net_pay")));p.setLocked(integer(r.get("locked"))); Object at=r.get("calculated_at"); if(at instanceof Timestamp t)p.setCalculatedAt(t.toLocalDateTime()); else if(at instanceof LocalDateTime dt)p.setCalculatedAt(dt); return p; }
    private BigDecimal money(BigDecimal value) { return (value == null ? ZERO : value).setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal value(Object value) { return value == null ? ZERO : money(new BigDecimal(value.toString())); }
    private BigDecimal maxZero(BigDecimal value) { return value.signum() < 0 ? ZERO : money(value); }
    private Long number(Object value) { return ((Number)value).longValue(); }
    private int integer(Object value) { return value == null ? 0 : ((Number)value).intValue(); }
    private String text(Object value) { return value == null ? null : value.toString(); }
    private LocalDate localDate(Object value) { if(value instanceof java.sql.Date d)return d.toLocalDate(); if(value instanceof LocalDate d)return d; return value == null ? null : LocalDate.parse(value.toString()); }
    private YearMonth parseMonth(String month) { try{return YearMonth.parse(month);}catch(Exception e){throw new BusinessException("Month must be yyyy-MM");} }
    private record MonthAmounts(BigDecimal base, BigDecimal performance, BigDecimal hours, BigDecimal overtimePay, BigDecimal gross, BigDecimal social, BigDecimal special) {}

    @Override
    public List<Map<String,Object>> listInputs(String kind, String salaryMonth, Long employeeId) {
        parseMonth(salaryMonth);
        String table = inputTable(kind);
        String sql = "SELECT " + inputColumns(kind) + ",e.employee_code,e.full_name AS employee_name FROM " + table
                + " m JOIN hr_employee e ON e.id=m.employee_id WHERE m.salary_month=?";
        List<Object> args = new ArrayList<>(List.of(salaryMonth));
        if (employeeId != null) { sql += " AND m.employee_id=?"; args.add(employeeId); }
        sql += " ORDER BY e.employee_code";
        return jdbc.queryForList(sql, args.toArray());
    }
    private String inputColumns(String kind) {
        return switch (kind) {
            case "performance" -> "m.id,m.employee_id,m.salary_month,m.amount,m.note,m.created_at,m.updated_at";
            case "overtime" -> "m.id,m.employee_id,m.salary_month,m.overtime_hours,m.unit_rate,m.overtime_pay,m.note,m.created_at,m.updated_at";
            case "social-insurance" -> "m.id,m.employee_id,m.salary_month,m.pension,m.medical,m.unemployment,m.housing_fund,m.created_at,m.updated_at";
            case "special-deductions" -> "m.id,m.employee_id,m.salary_month,m.children_education,m.continuing_education,m.housing_loan_interest,m.housing_rent,m.elderly_support,m.infant_care,m.other_deduction,m.created_at,m.updated_at";
            default -> throw new BusinessException("Unknown monthly input type");
        };
    }
    @Override @Transactional public void deleteInput(String kind,Long employeeId,String salaryMonth){parseMonth(salaryMonth);assertInputWritable(employeeId,salaryMonth);jdbc.update("DELETE FROM "+inputTable(kind)+" WHERE employee_id=? AND salary_month=?",employeeId,salaryMonth);}
    private String inputTable(String kind){return switch(kind){case "performance"->"hr_performance_month";case "overtime"->"hr_overtime_month";case "social-insurance"->"hr_social_insurance_month";case "special-deductions"->"hr_special_deduction_month";default->throw new BusinessException("Unknown monthly input type");};}

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportResultVO importPerformance(MultipartFile file, Long operatorUserId) {
        return importFile(file, row -> {
            MonthlyPerformanceRequest request = new MonthlyPerformanceRequest();
            request.setEmployeeId(employeeByCodeAndName(cell(row, 0), cell(row, 1)));
            request.setSalaryMonth(cell(row, 2));
            request.setAmount(decimal(cell(row, 3)));
            savePerformance(validated(request), operatorUserId);
            return new ImportTarget(request.getEmployeeId(), request.getSalaryMonth());
        }, 4);
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportResultVO importOvertime(MultipartFile file, Long operatorUserId) {
        return importFile(file, row -> {
            MonthlyOvertimeRequest request = new MonthlyOvertimeRequest();
            request.setEmployeeId(employeeByCodeAndName(cell(row, 0), cell(row, 1)));
            request.setSalaryMonth(cell(row, 2));
            request.setOvertimeHours(decimal(cell(row, 3)));
            saveOvertime(validated(request), operatorUserId);
            return new ImportTarget(request.getEmployeeId(), request.getSalaryMonth());
        }, 4);
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportResultVO importSocialInsurance(MultipartFile file) {
        return importFile(file, row -> {
            MonthlySocialInsuranceRequest request = new MonthlySocialInsuranceRequest();
            request.setEmployeeId(employeeByCodeAndName(cell(row, 0), cell(row, 1)));
            request.setSalaryMonth(cell(row, 2));
            request.setPension(decimal(cell(row, 3)));
            request.setMedical(decimal(cell(row, 4)));
            request.setUnemployment(decimal(cell(row, 5)));
            request.setHousingFund(decimal(cell(row, 6)));
            saveSocialInsurance(validated(request));
            return new ImportTarget(request.getEmployeeId(), request.getSalaryMonth());
        }, 7);
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportResultVO importSpecialDeduction(MultipartFile file) {
        return importFile(file, row -> {
            MonthlySpecialDeductionRequest request = new MonthlySpecialDeductionRequest();
            request.setEmployeeId(employeeByCodeAndName(cell(row, 0), cell(row, 1)));
            request.setSalaryMonth(cell(row, 2));
            request.setChildrenEducation(decimal(cell(row, 3)));
            request.setContinuingEducation(decimal(cell(row, 4)));
            request.setHousingLoanInterest(decimal(cell(row, 5)));
            request.setHousingRent(decimal(cell(row, 6)));
            request.setElderlySupport(decimal(cell(row, 7)));
            request.setInfantCare(decimal(cell(row, 8)));
            request.setOtherDeduction(decimal(cell(row, 9)));
            saveSpecialDeduction(validated(request));
            return new ImportTarget(request.getEmployeeId(), request.getSalaryMonth());
        }, 10);
    }

    private ImportResultVO importFile(MultipartFile file, RowConsumer consumer, int columns) {
        requireXlsx(file);
        ImportResultVO result = new ImportResultVO();
        try (Workbook book = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = book.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || cell(row, 0).isBlank()) continue;
                try {
                    if (row.getLastCellNum() < columns) throw new BusinessException("Missing required columns");
                    ImportTarget target = consumer.accept(row);
                    result.success(i + 1, "Imported", target.employeeId(), target.salaryMonth());
                } catch (Exception ex) {
                    result.failure(i + 1, ex.getMessage());
                }
            }
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Unable to read xlsx: " + e.getMessage());
        }
    }
    private void requireXlsx(MultipartFile file) { String name=file==null?null:file.getOriginalFilename();if(file==null||file.isEmpty()||file.getSize()>5*1024*1024||name==null||!name.toLowerCase(Locale.ROOT).endsWith(".xlsx"))throw new BusinessException("Only .xlsx files up to 5MB are supported"); }
    private String cell(Row row,int index){
        Cell cell = row.getCell(index);
        if (cell == null) return "";
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.STRING) return cell.getStringCellValue().trim();
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) return YearMonth.from(cell.getLocalDateTimeCellValue()).toString();
            String raw = cell instanceof XSSFCell xssfCell ? xssfCell.getRawValue() : null;
            return new BigDecimal(raw == null ? Double.toString(cell.getNumericCellValue()) : raw).stripTrailingZeros().toPlainString();
        }
        if (type == CellType.BOOLEAN) return Boolean.toString(cell.getBooleanCellValue());
        return "";
    }
    private BigDecimal decimal(String text){try{return new BigDecimal(text);}catch(Exception e){throw new BusinessException("Invalid amount");}}
    private Long employeeByCodeAndName(String code,String name){List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,full_name FROM hr_employee WHERE employee_code=?",code);if(rows.isEmpty())throw new BusinessException("Employee code not found: "+code);if(!Objects.equals(name,text(rows.get(0).get("full_name"))))throw new BusinessException("Employee name does not match code: "+code);return number(rows.get(0).get("id"));}
    private <T> T validated(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) throw new BusinessException(violations.iterator().next().getMessage());
        return request;
    }
    private record ImportTarget(Long employeeId, String salaryMonth) {}
    @FunctionalInterface private interface RowConsumer { ImportTarget accept(Row row); }

    @Override
    public byte[] exportPayroll(String salaryMonth, Long employeeId) {
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Payroll");
            String[] headers = PayrollExportTemplate.HEADERS;
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            List<PayrollVO> payrolls = queryPayroll(salaryMonth, employeeId, false);
            List<Long> ids = payrolls.stream().map(PayrollVO::getEmployeeId).toList();
            Map<Long, Map<String, Object>> socialByEmployee = monthlyRows("hr_social_insurance_month", salaryMonth, ids);
            Map<Long, Map<String, Object>> specialByEmployee = monthlyRows("hr_special_deduction_month", salaryMonth, ids);
            CellStyle moneyStyle = book.createCellStyle();
            moneyStyle.setDataFormat(book.createDataFormat().getFormat("0.00"));
            String[] socialKeys = {"pension", "medical", "unemployment", "housing_fund"};
            String[] deductionKeys = {"children_education", "continuing_education", "housing_loan_interest", "housing_rent", "elderly_support", "infant_care", "other_deduction"};
            for (int r = 0; r < payrolls.size(); r++) {
                PayrollVO p = payrolls.get(r);
                Row row = sheet.createRow(r + 1);
                row.createCell(0).setCellValue(p.getEmployeeCode());
                row.createCell(1).setCellValue(p.getEmployeeName());
                row.createCell(2).setCellValue("居民身份证");
                row.createCell(3).setCellValue(p.getIdCardNo());
                setMoney(row.createCell(4), p.getGrossIncome(), moneyStyle);
                setMoney(row.createCell(5), ZERO, moneyStyle);
                Map<String, Object> social = socialByEmployee.getOrDefault(p.getEmployeeId(), Map.of());
                Map<String, Object> special = specialByEmployee.getOrDefault(p.getEmployeeId(), Map.of());
                for (int i = 0; i < socialKeys.length; i++) setMoney(row.createCell(i + 6), value(social.get(socialKeys[i])), moneyStyle);
                for (int i = 0; i < deductionKeys.length; i++) setMoney(row.createCell(i + 10), value(special.get(deductionKeys[i])), moneyStyle);
                setMoney(row.createCell(17), p.getCurrentTaxWithheld(), moneyStyle);
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            book.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("Unable to create payroll export: " + e.getMessage());
        }
    }

    private Map<Long, Map<String, Object>> monthlyRows(String table, String month, List<Long> employeeIds) {
        if (employeeIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(employeeIds.size(), "?"));
        List<Object> args = new ArrayList<>(employeeIds);
        args.add(month);
        return jdbc.queryForList("SELECT * FROM " + table + " WHERE employee_id IN (" + placeholders + ") AND salary_month=?", args.toArray())
                .stream().collect(java.util.stream.Collectors.toMap(row -> number(row.get("employee_id")), row -> row, (first, ignored) -> first));
    }

    private void setMoney(Cell cell, BigDecimal value, CellStyle style) {
        BigDecimal normalized = money(value);
        cell.setCellStyle(style);
        if (cell instanceof XSSFCell xssfCell) {
            xssfCell.getCTCell().setT(STCellType.N);
            xssfCell.getCTCell().setV(normalized.toPlainString());
        } else {
            cell.setCellValue(normalized.toPlainString());
        }
    }
}
