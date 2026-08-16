package com.autohr.modules.hr.service.impl;

import com.autohr.modules.hr.dto.EmployeeSaveRequest;
import com.autohr.modules.hr.dto.EmployeeVO;
import com.autohr.modules.hr.dto.ImportResultVO;
import com.autohr.modules.hr.service.HrService;
import jakarta.validation.Validation;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeExcelServiceImplTest {
    private static final String DUPLICATE_SQL = "SELECT COUNT(*) FROM hr_employee WHERE employee_code=?";

    @Test
    void templateKeepsIdentifiersAsTextColumns() throws Exception {
        EmployeeExcelServiceImpl service = new EmployeeExcelServiceImpl(null, null,
                Validation.buildDefaultValidatorFactory().getValidator());

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(service.template()))) {
            Sheet sheet = workbook.getSheetAt(0);
            String[] actual = new String[sheet.getRow(0).getLastCellNum()];
            for (int index = 0; index < actual.length; index++) {
                actual[index] = sheet.getRow(0).getCell(index).getStringCellValue();
            }
            assertArrayEquals(new String[]{"工号", "姓名", "身份证号", "手机号", "邮箱", "招聘专业", "岗位编码",
                    "部门名称", "基本薪资", "个人加班单价", "银行卡号", "开户银行", "入职日期"}, actual);
            assertEquals("@", sheet.getColumnStyle(0).getDataFormatString());
            assertEquals("@", sheet.getColumnStyle(2).getDataFormatString());
            assertEquals("@", sheet.getColumnStyle(3).getDataFormatString());
            assertEquals("@", sheet.getColumnStyle(10).getDataFormatString());
        }
    }

    @Test
    void duplicateEmployeeCodeFailsThatRowWithoutOverwritingOrStoppingLaterRows() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        HrService hrService = mock(HrService.class);
        EmployeeExcelServiceImpl service = new EmployeeExcelServiceImpl(jdbc, hrService,
                Validation.buildDefaultValidatorFactory().getValidator());
        when(jdbc.queryForObject(DUPLICATE_SQL, Long.class, "E001")).thenReturn(1L);
        when(jdbc.queryForObject(DUPLICATE_SQL, Long.class, "E002")).thenReturn(0L);
        when(jdbc.queryForList("SELECT id FROM recruitment_job WHERE job_code=?", "J1"))
                .thenReturn(List.of(Map.of("id", 5L)));
        when(jdbc.queryForList("SELECT id FROM hr_department WHERE department_name=?", "研发部"))
                .thenReturn(List.of(Map.of("id", 7L)));
        EmployeeVO saved = new EmployeeVO();
        saved.setId(88L);
        when(hrService.saveEmployee(any(EmployeeSaveRequest.class), eq(99L))).thenReturn(saved);
        MockMultipartFile file = new MockMultipartFile("file", "employees.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookWithTwoEmployees());

        ImportResultVO result = service.importEmployees(file, 99L);

        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertFalse(result.getRows().get(0).isSuccess());
        assertEquals(2, result.getRows().get(0).getRow());
        assertEquals(88L, result.getRows().get(1).getEmployeeId());
        assertEquals("2026-08", result.getRows().get(1).getSalaryMonth());
        verify(hrService).saveEmployee(any(EmployeeSaveRequest.class), eq(99L));
    }

    private byte[] workbookWithTwoEmployees() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("员工导入");
            sheet.createRow(0).createCell(0).setCellValue("工号");
            employeeRow(sheet.createRow(1), "E001", "重复员工", "01");
            employeeRow(sheet.createRow(2), "E002", "新员工", "02");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void employeeRow(Row row, String code, String name, String suffix) {
        String[] values = {code, name, "1101011990010100" + suffix, "138000000" + suffix, "", "计算机",
                "J1", "研发部", "10000.00", "", "62220000000000" + suffix, "测试银行", "2026-08-01"};
        for (int index = 0; index < values.length; index++) {
            row.createCell(index).setCellValue(values[index]);
        }
    }
}
