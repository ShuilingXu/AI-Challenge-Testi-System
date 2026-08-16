package com.autohr.modules.hr.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.service.AuditLogService;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.hr.dto.ImportResultVO;
import com.autohr.modules.hr.service.EmployeeExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;

@RestController @RequestMapping("/api/hr/employees") @RequiredArgsConstructor
public class EmployeeExcelController {
    private final EmployeeExcelService service;private final AuthService authService;private final AuditLogService auditLogService;
    @GetMapping("/template") public ResponseEntity<byte[]> template(){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("employee-import-template.xlsx",StandardCharsets.UTF_8).build().toString()).contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(service.template());}
    @PostMapping("/import") public ApiResponse<ImportResultVO> importFile(Authentication auth,@RequestParam MultipartFile file){SessionUserVO user=authService.loadUserByUsername(auth.getName());ImportResultVO result=service.importEmployees(file,user.getId());result.getRows().stream().filter(ImportResultVO.RowResult::isSuccess).forEach(row->auditLogService.log(user.getId(),user.getDisplayName(),user.getRoleCode(),"PAYROLL","CREATE_EMPLOYEE_SALARY","HR_EMPLOYEE",String.valueOf(row.getEmployeeId()),"row="+row.getRow()+", month="+row.getSalaryMonth()));auditLogService.log(user.getId(),user.getDisplayName(),user.getRoleCode(),"PAYROLL","EMPLOYEE_IMPORT","HR_EMPLOYEE","BATCH","success="+result.getSuccessCount()+", failure="+result.getFailureCount());return ApiResponse.success(result);}
}
