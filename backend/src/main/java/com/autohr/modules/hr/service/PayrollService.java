package com.autohr.modules.hr.service;

import com.autohr.modules.hr.dto.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

public interface PayrollService {
    void savePerformance(MonthlyPerformanceRequest request, Long operatorUserId);
    void saveOvertime(MonthlyOvertimeRequest request, Long operatorUserId);
    void saveSocialInsurance(MonthlySocialInsuranceRequest request);
    void saveSpecialDeduction(MonthlySpecialDeductionRequest request);
    List<PayrollVO> generate(PayrollGenerateRequest request);
    List<PayrollVO> listPayroll(String salaryMonth, Long employeeId);
    void setLocked(Long employeeId, String salaryMonth, boolean locked);
    void deletePayroll(Long employeeId, String salaryMonth);
    ImportResultVO importPerformance(MultipartFile file, Long operatorUserId);
    ImportResultVO importOvertime(MultipartFile file, Long operatorUserId);
    ImportResultVO importSocialInsurance(MultipartFile file);
    ImportResultVO importSpecialDeduction(MultipartFile file);
    byte[] exportPayroll(String salaryMonth, Long employeeId);
    List<Map<String,Object>> listInputs(String kind, String salaryMonth, Long employeeId);
    void deleteInput(String kind, Long employeeId, String salaryMonth);
}
