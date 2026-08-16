package com.autohr.modules.hr.service;

import com.autohr.common.exception.BusinessException;
public final class PayrollMutationGuard { private PayrollMutationGuard(){} public static void requireWritable(boolean locked){if(locked)throw new BusinessException("Payroll is locked for this month");} }
