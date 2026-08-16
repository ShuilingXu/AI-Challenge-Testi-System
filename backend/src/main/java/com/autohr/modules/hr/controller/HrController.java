package com.autohr.modules.hr.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.common.api.PageQuery;
import com.autohr.common.api.PageResponse;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.auth.service.AuditLogService;
import com.autohr.modules.hr.dto.DepartmentSaveRequest;
import com.autohr.modules.hr.dto.DepartmentVO;
import com.autohr.modules.hr.dto.EmployeeSaveRequest;
import com.autohr.modules.hr.dto.EmployeeVO;
import com.autohr.modules.hr.dto.HrDashboardVO;
import com.autohr.modules.hr.dto.IntegrationBindingSaveRequest;
import com.autohr.modules.hr.dto.IntegrationBindingVO;
import com.autohr.modules.hr.service.HrService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hr")
@RequiredArgsConstructor
public class HrController {

    private final HrService hrService;
    private final AuthService authService;
    private final AuditLogService auditLogService;

    @GetMapping("/dashboard")
    public ApiResponse<HrDashboardVO> getDashboard() {
        return ApiResponse.success(hrService.getDashboard());
    }

    @PostMapping("/departments")
    public ApiResponse<DepartmentVO> saveDepartment(Authentication authentication,
                                                    @Valid @RequestBody DepartmentSaveRequest request) {
        DepartmentVO saved = hrService.saveDepartment(request);
        SessionUserVO current = currentUser(authentication);
        auditLogService.log(current.getId(), current.getDisplayName(), current.getRoleCode(), "ADMIN", request.getId() == null ? "CREATE_DEPARTMENT" : "UPDATE_DEPARTMENT", "HR_DEPARTMENT", String.valueOf(saved.getId()), saved.getDepartmentName());
        return ApiResponse.success(saved);
    }

    @GetMapping("/departments")
    public ApiResponse<PageResponse<DepartmentVO>> listDepartments(@RequestParam(required = false) Long parentDepartmentId,
                                                           @RequestParam(required = false) Integer status,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) Integer page,
                                                           @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(hrService.listDepartments(parentDepartmentId, status, keyword,
                PageQuery.of(page, pageSize)));
    }

    @DeleteMapping("/departments/{id}")
    public ApiResponse<Void> deleteDepartment(Authentication authentication,
                                             @PathVariable Long id) {
        hrService.deleteDepartment(id);
        SessionUserVO current = currentUser(authentication);
        auditLogService.log(current.getId(), current.getDisplayName(), current.getRoleCode(), "ADMIN", "DELETE_DEPARTMENT", "HR_DEPARTMENT", String.valueOf(id), "删除部门");
        return ApiResponse.success("deleted", null);
    }

    @PostMapping("/employees")
    public ApiResponse<EmployeeVO> saveEmployee(Authentication authentication,
                                                @Valid @RequestBody EmployeeSaveRequest request) {
        SessionUserVO current = currentUser(authentication);
        EmployeeVO saved = hrService.saveEmployee(request, current.getId());
        auditLogService.log(current.getId(), current.getDisplayName(), current.getRoleCode(), "PAYROLL", request.getId() == null ? "CREATE_EMPLOYEE_SALARY" : "UPDATE_EMPLOYEE_SALARY", "HR_EMPLOYEE", String.valueOf(saved.getId()), saved.getFullName());
        return ApiResponse.success(saved);
    }

    @GetMapping("/employees")
    public ApiResponse<PageResponse<EmployeeVO>> listEmployees(@RequestParam(required = false) Long departmentId,
                                                       @RequestParam(required = false) Integer employmentStatus,
                                                       @RequestParam(required = false) String name,
                                                       @RequestParam(required = false) String employeeCode,
                                                       @RequestParam(required = false) String mobilePhone,
                                                       @RequestParam(required = false, defaultValue = "false") Boolean mobileExact,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(hrService.listEmployees(departmentId, employmentStatus, name, employeeCode, mobilePhone, mobileExact, keyword,
                PageQuery.of(page, pageSize)));
    }

    @GetMapping("/employees/{id}")
    public ApiResponse<EmployeeVO> getEmployee(@PathVariable Long id) {
        return ApiResponse.success(hrService.getEmployee(id));
    }

    @DeleteMapping("/employees/{id}")
    public ApiResponse<Void> deleteEmployee(Authentication authentication,
                                           @PathVariable Long id) {
        hrService.deleteEmployee(id);
        SessionUserVO current = currentUser(authentication);
        auditLogService.log(current.getId(), current.getDisplayName(), current.getRoleCode(), "ADMIN", "DELETE_EMPLOYEE", "HR_EMPLOYEE", String.valueOf(id), "删除员工");
        return ApiResponse.success("deleted", null);
    }

    @PostMapping("/bindings")
    public ApiResponse<IntegrationBindingVO> saveBinding(Authentication authentication,
                                                         @Valid @RequestBody IntegrationBindingSaveRequest request) {
        IntegrationBindingVO saved = hrService.saveBinding(request);
        SessionUserVO current = currentUser(authentication);
        auditLogService.log(current.getId(), current.getDisplayName(), current.getRoleCode(), "ADMIN", request.getId() == null ? "CREATE_BINDING" : "UPDATE_BINDING", "HR_INTEGRATION_BINDING", String.valueOf(saved.getId()), saved.getModuleCode());
        return ApiResponse.success(saved);
    }

    @GetMapping("/bindings")
    public ApiResponse<PageResponse<IntegrationBindingVO>> listBindings(@RequestParam(required = false) String moduleCode,
                                                                @RequestParam(required = false) Long employeeId,
                                                                @RequestParam(required = false) Long departmentId,
                                                                @RequestParam(required = false) Integer page,
                                                                @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(hrService.listBindings(moduleCode, employeeId, departmentId,
                PageQuery.of(page, pageSize)));
    }

    private SessionUserVO currentUser(Authentication authentication) {
        return authService.loadUserByUsername(authentication.getName());
    }
}
