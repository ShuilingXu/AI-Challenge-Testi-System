package com.autohr.modules.hr.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Stateless money rules used by payroll persistence and unit tests. */
public final class PayrollCalculations {
    private PayrollCalculations() { }
    public static BigDecimal money(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP); }
    public static BigDecimal resolveOvertimeRate(BigDecimal personalRate, BigDecimal jobDefaultRate) { return money(personalRate == null ? jobDefaultRate : personalRate); }
    public static BigDecimal overtimePay(BigDecimal hours, BigDecimal rate) { return money(money(hours).multiply(resolveOvertimeRate(rate, BigDecimal.ZERO))); }
    public static BigDecimal currentWithholding(BigDecimal cumulativeTaxableIncome, BigDecimal previouslyWithheld) { return maxZero(cumulativeTax(cumulativeTaxableIncome).subtract(money(previouslyWithheld))); }
    public static BigDecimal cumulativeTax(BigDecimal taxable) { BigDecimal amount=maxZero(taxable); if(amount.compareTo(new BigDecimal("36000"))<=0)return money(amount.multiply(new BigDecimal("0.03"))); if(amount.compareTo(new BigDecimal("144000"))<=0)return money(amount.multiply(new BigDecimal("0.10")).subtract(new BigDecimal("2520"))); if(amount.compareTo(new BigDecimal("300000"))<=0)return money(amount.multiply(new BigDecimal("0.20")).subtract(new BigDecimal("16920"))); if(amount.compareTo(new BigDecimal("420000"))<=0)return money(amount.multiply(new BigDecimal("0.25")).subtract(new BigDecimal("31920"))); if(amount.compareTo(new BigDecimal("660000"))<=0)return money(amount.multiply(new BigDecimal("0.30")).subtract(new BigDecimal("52920"))); if(amount.compareTo(new BigDecimal("960000"))<=0)return money(amount.multiply(new BigDecimal("0.35")).subtract(new BigDecimal("85920"))); return money(amount.multiply(new BigDecimal("0.45")).subtract(new BigDecimal("181920"))); }
    private static BigDecimal maxZero(BigDecimal value){return value==null||value.signum()<0?BigDecimal.ZERO.setScale(2):money(value);}
}
