package com.autohr.modules.hr.service.impl;

import com.autohr.common.exception.BusinessException;
import com.autohr.modules.hr.dto.EmployeeSaveRequest;
import com.autohr.modules.hr.dto.EmployeeVO;
import com.autohr.modules.hr.dto.HrDashboardVO;
import com.autohr.modules.hr.dto.HrStatisticsVO;
import com.autohr.modules.hr.entity.Department;
import com.autohr.modules.hr.entity.Employee;
import com.autohr.modules.hr.entity.SalaryHistory;
import com.autohr.modules.hr.mapper.DepartmentMapper;
import com.autohr.modules.hr.mapper.EmployeeMapper;
import com.autohr.modules.hr.mapper.IntegrationBindingMapper;
import com.autohr.modules.hr.mapper.SalaryHistoryMapper;
import com.autohr.modules.hr.service.HrStatisticsService;
import com.autohr.modules.recruitment.entity.RecruitmentJob;
import com.autohr.modules.recruitment.mapper.RecruitmentJobMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrServiceImplTest {

    @Mock
    private DepartmentMapper departmentMapper;

    @Mock
    private EmployeeMapper employeeMapper;

    @Mock
    private IntegrationBindingMapper integrationBindingMapper;

    @Mock private RecruitmentJobMapper recruitmentJobMapper;
    @Mock private SalaryHistoryMapper salaryHistoryMapper;
    @Mock private JdbcTemplate jdbc;
    @Mock private HrStatisticsService hrStatisticsService;

    @InjectMocks
    private HrServiceImpl hrService;

    @Test
    void createsAnEmployeeWithoutLookingUpEmployeeCode() {
        Department department = department(7L);
        Employee inserted = new Employee();
        inserted.setId(42L);
        inserted.setDepartmentId(department.getId());
        inserted.setEmployeeCode("EMP-42");
        inserted.setFullName("Test User");
        when(departmentMapper.selectById(department.getId())).thenReturn(department);
        when(recruitmentJobMapper.selectById(3L)).thenReturn(job(3L));
        when(salaryHistoryMapper.selectOne(any())).thenReturn(null);
        when(employeeMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            employee.setId(inserted.getId());
            return 1;
        }).when(employeeMapper).insert(any(Employee.class));
        when(employeeMapper.selectById(inserted.getId())).thenReturn(inserted);
        when(departmentMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(department));
        when(employeeMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(inserted));

        EmployeeSaveRequest request = employeeRequest(null);
        request.setEmployeeCode("EMP-42");
        EmployeeVO saved = hrService.saveEmployee(request, 1L);

        assertEquals(inserted.getId(), saved.getId());
        verify(employeeMapper).insert(any(Employee.class));
        verify(employeeMapper, never()).selectOne(any());
        verify(employeeMapper, never()).updateById(any(Employee.class));
    }

    @Test
    void rejectsAnUpdateForAnUnknownExplicitId() {
        Department department = department(7L);
        when(departmentMapper.selectById(department.getId())).thenReturn(department);
        when(employeeMapper.selectCount(any())).thenReturn(0L);

        assertThrows(BusinessException.class, () -> hrService.saveEmployee(employeeRequest(99L), 1L));

        verify(employeeMapper, never()).insert(any(Employee.class));
        verify(employeeMapper, never()).updateById(any(Employee.class));
    }

    @Test
    void salaryChangeToZeroDefaultsHistoryToTheHireMonthAndConfirmsTheSalary() {
        Department department = department(7L);
        Employee employee = new Employee();
        employee.setId(42L);
        employee.setEmployeeCode("EMP-42");
        employee.setFullName("Test User");
        employee.setDepartmentId(7L);
        employee.setJobId(3L);
        employee.setHireDate(LocalDate.of(2024, 3, 18));
        employee.setBaseSalary(new BigDecimal("10000.00"));
        employee.setSalaryConfirmed(0);
        SalaryHistory history = new SalaryHistory();
        history.setId(11L);
        history.setEmployeeId(42L);

        when(departmentMapper.selectById(7L)).thenReturn(department);
        when(employeeMapper.selectCount(any())).thenReturn(0L);
        when(employeeMapper.selectById(42L)).thenReturn(employee);
        when(recruitmentJobMapper.selectById(3L)).thenReturn(job(3L));
        when(salaryHistoryMapper.selectOne(any())).thenReturn(history);
        when(departmentMapper.selectList(any())).thenReturn(List.of(department));
        when(employeeMapper.selectList(any())).thenReturn(List.of(employee));
        EmployeeSaveRequest request = employeeRequest(42L);
        request.setBaseSalary(BigDecimal.ZERO);
        request.setSalaryChangeReason("协商调整");

        hrService.saveEmployee(request, 99L);

        assertEquals(new BigDecimal("0.00"), employee.getBaseSalary());
        assertEquals(1, employee.getSalaryConfirmed());
        assertEquals(new BigDecimal("10000.00"), history.getBaseSalaryBefore());
        assertEquals(new BigDecimal("0.00"), history.getBaseSalaryAfter());
        assertEquals("2024-03", history.getEffectiveMonth());
        assertEquals(LocalDate.of(2024, 3, 18), employee.getHireDate());
        assertEquals("协商调整", history.getReason());
        assertEquals(99L, history.getOperatorUserId());
        verify(salaryHistoryMapper).updateById(history);
    }

    @Test
    void salaryChangeRejectsDirtyEmployeeWithoutHireDateOrExplicitEffectiveMonth() {
        Department department = department(7L);
        Employee employee = new Employee();
        employee.setId(42L);
        employee.setEmployeeCode("EMP-42");
        employee.setDepartmentId(7L);
        employee.setJobId(3L);
        employee.setBaseSalary(new BigDecimal("10000.00"));

        when(departmentMapper.selectById(7L)).thenReturn(department);
        when(employeeMapper.selectCount(any())).thenReturn(0L);
        when(employeeMapper.selectById(42L)).thenReturn(employee);
        when(recruitmentJobMapper.selectById(3L)).thenReturn(job(3L));
        EmployeeSaveRequest request = employeeRequest(42L);
        request.setBaseSalary(BigDecimal.ZERO);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> hrService.saveEmployee(request, 99L));

        assertEquals("Employee hire date is required when effective month is blank", exception.getMessage());
        verify(employeeMapper, never()).updateById(any(Employee.class));
        verify(salaryHistoryMapper, never()).insert(any(SalaryHistory.class));
    }

    @Test
    void dashboardReusesOneCompleteStatisticsSnapshot() {
        HrStatisticsVO statistics = new HrStatisticsVO();
        statistics.getSalary().setAverageGross(new BigDecimal("12345.67"));
        when(hrStatisticsService.statistics(anyString())).thenReturn(statistics);

        HrDashboardVO dashboard = hrService.getDashboard();

        assertSame(statistics, dashboard.getStatistics());
        assertEquals(new BigDecimal("12345.67"), dashboard.getAverageGrossSalary());
        verify(hrStatisticsService, times(1)).statistics(anyString());
    }

    private Department department(Long id) {
        Department department = new Department();
        department.setId(id);
        department.setDepartmentCode("TEST");
        return department;
    }

    private RecruitmentJob job(Long id) { RecruitmentJob job = new RecruitmentJob(); job.setId(id); job.setJobTitle("Engineer"); return job; }

    private EmployeeSaveRequest employeeRequest(Long id) {
        EmployeeSaveRequest request = new EmployeeSaveRequest();
        request.setId(id);
        request.setFullName("Test User");
        request.setIdCardNo("110101199001011234");
        request.setMobilePhone("13800138000");
        request.setRecruitmentMajor("Engineering");
        request.setJobId(3L);
        request.setBaseSalary(new java.math.BigDecimal("10000"));
        request.setDepartmentId(7L);
        request.setBankAccountNo("6222020000000000");
        request.setBankName("Test Bank");
        return request;
    }
}
