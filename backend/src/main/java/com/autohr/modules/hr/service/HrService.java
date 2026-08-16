package com.autohr.modules.hr.service;

import com.autohr.common.api.PageQuery;
import com.autohr.common.api.PageResponse;
import com.autohr.modules.hr.dto.DepartmentSaveRequest;
import com.autohr.modules.hr.dto.DepartmentVO;
import com.autohr.modules.hr.dto.EmployeeSaveRequest;
import com.autohr.modules.hr.dto.EmployeeVO;
import com.autohr.modules.hr.dto.HrDashboardVO;
import com.autohr.modules.hr.dto.IntegrationBindingSaveRequest;
import com.autohr.modules.hr.dto.IntegrationBindingVO;

public interface HrService {

    DepartmentVO saveDepartment(DepartmentSaveRequest request);

    PageResponse<DepartmentVO> listDepartments(Long parentDepartmentId, Integer status, String keyword, PageQuery pageQuery);

    void deleteDepartment(Long id);

    EmployeeVO saveEmployee(EmployeeSaveRequest request, Long operatorUserId);

    EmployeeVO getEmployee(Long id);

    PageResponse<EmployeeVO> listEmployees(Long departmentId, Integer employmentStatus, String name, String employeeCode,
                                           String mobilePhone, String keyword, PageQuery pageQuery);
    PageResponse<EmployeeVO> listEmployees(Long departmentId, Integer employmentStatus, String name, String employeeCode,
                                           String mobilePhone, Boolean mobileExact, String keyword, PageQuery pageQuery);

    void deleteEmployee(Long id);

    IntegrationBindingVO saveBinding(IntegrationBindingSaveRequest request);

    PageResponse<IntegrationBindingVO> listBindings(String moduleCode, Long employeeId, Long departmentId, PageQuery pageQuery);

    HrDashboardVO getDashboard();
}
