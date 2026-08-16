package com.autohr.modules.hr.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.service.AuditLogService;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.hr.dto.*;
import com.autohr.modules.hr.service.PayrollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hr/payroll")
@RequiredArgsConstructor
public class PayrollController {
    private final PayrollService payrollService;
    private final AuthService authService;
    private final AuditLogService auditLogService;

    @PostMapping("/performance") public ApiResponse<Void> savePerformance(Authentication auth, @Valid @RequestBody MonthlyPerformanceRequest request) { SessionUserVO user=user(auth); payrollService.savePerformance(request,user.getId()); audit(user,"PERFORMANCE_SAVE",request.getEmployeeId(),request.getSalaryMonth()); return ApiResponse.success("saved",null); }
    @PostMapping("/overtime") public ApiResponse<Void> saveOvertime(Authentication auth, @Valid @RequestBody MonthlyOvertimeRequest request) { SessionUserVO user=user(auth); payrollService.saveOvertime(request,user.getId()); audit(user,"OVERTIME_SAVE",request.getEmployeeId(),request.getSalaryMonth()); return ApiResponse.success("saved",null); }
    @PostMapping("/social-insurance") public ApiResponse<Void> saveSocial(Authentication auth, @Valid @RequestBody MonthlySocialInsuranceRequest request) { SessionUserVO user=user(auth); payrollService.saveSocialInsurance(request); audit(user,"SOCIAL_INSURANCE_SAVE",request.getEmployeeId(),request.getSalaryMonth()); return ApiResponse.success("saved",null); }
    @PostMapping("/special-deductions") public ApiResponse<Void> saveSpecial(Authentication auth, @Valid @RequestBody MonthlySpecialDeductionRequest request) { SessionUserVO user=user(auth); payrollService.saveSpecialDeduction(request); audit(user,"SPECIAL_DEDUCTION_SAVE",request.getEmployeeId(),request.getSalaryMonth()); return ApiResponse.success("saved",null); }
    @PostMapping("/generate") public ApiResponse<List<PayrollVO>> generate(Authentication auth, @Valid @RequestBody PayrollGenerateRequest request) { SessionUserVO user=user(auth); List<PayrollVO> items=payrollService.generate(request); audit(user,"PAYROLL_GENERATE",request.getEmployeeId(),request.getSalaryMonth()); return ApiResponse.success(items); }
    @GetMapping public ApiResponse<List<PayrollVO>> list(@RequestParam String salaryMonth,@RequestParam(required=false) Long employeeId){ return ApiResponse.success(payrollService.listPayroll(salaryMonth,employeeId)); }
    @GetMapping("/inputs/{kind}") public ApiResponse<List<Map<String,Object>>> inputs(@PathVariable String kind,@RequestParam String salaryMonth,@RequestParam(required=false) Long employeeId){return ApiResponse.success(payrollService.listInputs(kind,salaryMonth,employeeId));}
    @DeleteMapping("/inputs/{kind}/{employeeId}/{salaryMonth}") public ApiResponse<Void> deleteInput(Authentication auth,@PathVariable String kind,@PathVariable Long employeeId,@PathVariable String salaryMonth){SessionUserVO user=user(auth);requirePayrollAdmin(user);payrollService.deleteInput(kind,employeeId,salaryMonth);audit(user,kind.toUpperCase()+"_DELETE",employeeId,salaryMonth);return ApiResponse.success("deleted",null);}
    @PostMapping("/{employeeId}/{salaryMonth}/lock") public ApiResponse<Void> lock(Authentication auth,@PathVariable Long employeeId,@PathVariable String salaryMonth){SessionUserVO user=user(auth);requirePayrollAdmin(user);payrollService.setLocked(employeeId,salaryMonth,true);audit(user,"PAYROLL_LOCK",employeeId,salaryMonth);return ApiResponse.success("locked",null);}
    @PostMapping("/{employeeId}/{salaryMonth}/unlock") public ApiResponse<Void> unlock(Authentication auth,@PathVariable Long employeeId,@PathVariable String salaryMonth){SessionUserVO user=user(auth);if(!"HR_ADMIN".equals(user.getRoleCode())&&!"IT_ADMIN".equals(user.getRoleCode()))throw new com.autohr.common.exception.BusinessException("Only HR_ADMIN or IT_ADMIN can unlock payroll");payrollService.setLocked(employeeId,salaryMonth,false);audit(user,"PAYROLL_UNLOCK",employeeId,salaryMonth);return ApiResponse.success("unlocked",null);}
    @DeleteMapping("/{employeeId}/{salaryMonth}") public ApiResponse<Void> deletePayroll(Authentication auth,@PathVariable Long employeeId,@PathVariable String salaryMonth){SessionUserVO user=user(auth);requirePayrollAdmin(user);payrollService.deletePayroll(employeeId,salaryMonth);audit(user,"PAYROLL_DELETE",employeeId,salaryMonth);return ApiResponse.success("deleted",null);}
    @PostMapping("/performance/import") public ApiResponse<ImportResultVO> importPerformance(Authentication auth,@RequestParam MultipartFile file){SessionUserVO user=user(auth);ImportResultVO r=payrollService.importPerformance(file,user.getId());auditImport(user,"PERFORMANCE_IMPORT",r);return ApiResponse.success(r);}
    @PostMapping("/overtime/import") public ApiResponse<ImportResultVO> importOvertime(Authentication auth,@RequestParam MultipartFile file){SessionUserVO user=user(auth);ImportResultVO r=payrollService.importOvertime(file,user.getId());auditImport(user,"OVERTIME_IMPORT",r);return ApiResponse.success(r);}
    @PostMapping("/social-insurance/import") public ApiResponse<ImportResultVO> importSocial(Authentication auth,@RequestParam MultipartFile file){SessionUserVO user=user(auth);ImportResultVO r=payrollService.importSocialInsurance(file);auditImport(user,"SOCIAL_INSURANCE_IMPORT",r);return ApiResponse.success(r);}
    @PostMapping("/special-deductions/import") public ApiResponse<ImportResultVO> importSpecial(Authentication auth,@RequestParam MultipartFile file){SessionUserVO user=user(auth);ImportResultVO r=payrollService.importSpecialDeduction(file);auditImport(user,"SPECIAL_DEDUCTION_IMPORT",r);return ApiResponse.success(r);}
    @GetMapping("/export") public ResponseEntity<byte[]> export(Authentication auth,@RequestParam String salaryMonth,@RequestParam(required=false) Long employeeId){SessionUserVO user=user(auth);audit(user,"PAYROLL_EXPORT",employeeId,salaryMonth);String filename="normal-wages-"+salaryMonth+".xlsx";return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename,StandardCharsets.UTF_8).build().toString()).contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(payrollService.exportPayroll(salaryMonth,employeeId));}
    private SessionUserVO user(Authentication auth){return authService.loadUserByUsername(auth.getName());}
    private void requirePayrollAdmin(SessionUserVO user){if(!"HR_ADMIN".equals(user.getRoleCode())&&!"IT_ADMIN".equals(user.getRoleCode()))throw new com.autohr.common.exception.BusinessException("Only HR_ADMIN or IT_ADMIN can modify payroll locks or delete payroll data");}
    private void audit(SessionUserVO user,String action,Long employeeId,String month){auditLogService.log(user.getId(),user.getDisplayName(),user.getRoleCode(),"PAYROLL",action,"HR_PAYROLL",employeeId==null?"BATCH":employeeId.toString(),month==null?action:month);}
    private void auditImport(SessionUserVO user,String action,ImportResultVO result){result.getRows().stream().filter(ImportResultVO.RowResult::isSuccess).forEach(row->audit(user,action+"_ROW",row.getEmployeeId(),row.getSalaryMonth()));auditLogService.log(user.getId(),user.getDisplayName(),user.getRoleCode(),"PAYROLL",action,"HR_PAYROLL","BATCH","success="+result.getSuccessCount()+", failure="+result.getFailureCount());}
}
