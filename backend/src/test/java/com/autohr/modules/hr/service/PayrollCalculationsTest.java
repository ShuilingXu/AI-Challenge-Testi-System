package com.autohr.modules.hr.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class PayrollCalculationsTest {
    @Test void cumulativeWithholdingMatchesSingleMonthAndLowerBound() { assertEquals(new BigDecimal("150.00"), PayrollCalculations.currentWithholding(new BigDecimal("5000"), BigDecimal.ZERO)); assertEquals(BigDecimal.ZERO.setScale(2), PayrollCalculations.currentWithholding(new BigDecimal("5000"), new BigDecimal("200"))); }
    @Test void cumulativeWithholdingCrossesTaxBracket() { assertEquals(new BigDecimal("1080.00"), PayrollCalculations.cumulativeTax(new BigDecimal("36000"))); assertEquals(new BigDecimal("1080.10"), PayrollCalculations.cumulativeTax(new BigDecimal("36001"))); assertEquals(new BigDecimal("11880.00"), PayrollCalculations.cumulativeTax(new BigDecimal("144000"))); assertEquals(new BigDecimal("1200.00"), PayrollCalculations.currentWithholding(new BigDecimal("144000"), new BigDecimal("10680"))); }
    @Test void yearMidHireUsesOnlyMonthsWorked() { BigDecimal income=new BigDecimal("30000").multiply(BigDecimal.valueOf(3)); BigDecimal taxable=income.subtract(new BigDecimal("5000").multiply(BigDecimal.valueOf(3))); assertEquals(new BigDecimal("4980.00"), PayrollCalculations.cumulativeTax(taxable)); }
    @Test void personalOvertimeRateOverridesJobIncludingZero() { assertEquals(new BigDecimal("60.00"), PayrollCalculations.overtimePay(new BigDecimal("3"), PayrollCalculations.resolveOvertimeRate(null,new BigDecimal("20")))); assertEquals(BigDecimal.ZERO.setScale(2), PayrollCalculations.overtimePay(new BigDecimal("3"), PayrollCalculations.resolveOvertimeRate(BigDecimal.ZERO,new BigDecimal("20")))); }
    @Test void lockedPayrollRejectsMonthlyMutations() { assertThrows(com.autohr.common.exception.BusinessException.class, () -> PayrollMutationGuard.requireWritable(true)); assertDoesNotThrow(() -> PayrollMutationGuard.requireWritable(false)); }
    @Test void itsExportHeadersHaveRequiredOrder() { assertArrayEquals(new String[]{"工号","姓名","证照类型","证照号码","本期收入","本期免税收入","基本养老保险费","基本医疗保险费","失业保险费","住房公积金","子女教育","继续教育","住房贷款利息","住房租金","赡养老人","婴幼儿照护","其他扣除","应纳税额"}, PayrollExportTemplate.HEADERS); }
}
