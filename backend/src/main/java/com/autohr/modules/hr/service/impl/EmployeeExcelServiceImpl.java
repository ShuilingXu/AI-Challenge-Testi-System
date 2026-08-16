package com.autohr.modules.hr.service.impl;

import com.autohr.common.exception.BusinessException;
import com.autohr.modules.hr.dto.EmployeeSaveRequest;
import com.autohr.modules.hr.dto.ImportResultVO;
import com.autohr.modules.hr.service.EmployeeExcelService;
import com.autohr.modules.hr.service.HrService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.*;

@Service @RequiredArgsConstructor
public class EmployeeExcelServiceImpl implements EmployeeExcelService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc; private final HrService hrService; private final Validator validator;
    private static final String[] HEADERS={"工号","姓名","身份证号","手机号","邮箱","招聘专业","岗位编码","部门名称","基本薪资","个人加班单价","银行卡号","开户银行","入职日期"};
    @Override public byte[] template(){try(Workbook book=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){Sheet sheet=book.createSheet("员工导入");Row header=sheet.createRow(0);CellStyle text=book.createCellStyle();text.setDataFormat(book.createDataFormat().getFormat("@"));for(int i=0;i<HEADERS.length;i++){header.createCell(i).setCellValue(HEADERS[i]);sheet.setColumnWidth(i,Math.min(28,Math.max(12,HEADERS[i].length()*4))*256);}for(int i:new int[]{0,2,3,10})sheet.setDefaultColumnStyle(i,text);book.write(out);return out.toByteArray();}catch(Exception e){throw new BusinessException("Unable to create employee template");}}
    @Override public ImportResultVO importEmployees(MultipartFile file,Long operatorUserId){String name=file==null?null:file.getOriginalFilename();if(file==null||file.isEmpty()||file.getSize()>5*1024*1024||name==null||!name.toLowerCase(Locale.ROOT).endsWith(".xlsx"))throw new BusinessException("Only .xlsx files up to 5MB are supported");ImportResultVO result=new ImportResultVO();try(Workbook book=WorkbookFactory.create(file.getInputStream())){Sheet sheet=book.getSheetAt(0);for(int i=1;i<=sheet.getLastRowNum();i++){Row row=sheet.getRow(i);if(row==null)continue;try{String code=cell(row,0);if(code.isBlank())continue;if(jdbc.queryForObject("SELECT COUNT(*) FROM hr_employee WHERE employee_code=?",Long.class,code)>0)throw new BusinessException("工号已存在，已跳过");Long jobId=id("SELECT id FROM recruitment_job WHERE job_code=?",cell(row,6),"岗位编码不存在");Long departmentId=id("SELECT id FROM hr_department WHERE department_name=?",cell(row,7),"部门不存在");EmployeeSaveRequest request=new EmployeeSaveRequest();request.setEmployeeCode(code);request.setFullName(cell(row,1));request.setIdCardNo(cell(row,2));request.setMobilePhone(cell(row,3));request.setEmail(cell(row,4));request.setRecruitmentMajor(cell(row,5));request.setJobId(jobId);request.setDepartmentId(departmentId);request.setBaseSalary(new BigDecimal(cell(row,8)));String overtime=cell(row,9);request.setOvertimeRate(overtime.isBlank()?null:new BigDecimal(overtime));request.setBankAccountNo(cell(row,10));request.setBankName(cell(row,11));String hire=cell(row,12);request.setHireDate(hire.isBlank()?LocalDate.now(BUSINESS_ZONE):LocalDate.parse(hire));request.setEmploymentStatus(1);var saved=hrService.saveEmployee(validated(request),operatorUserId);result.success(i+1,"导入成功",saved.getId(),YearMonth.now(BUSINESS_ZONE).toString());}catch(Exception e){result.failure(i+1,e.getMessage());}}return result;}catch(Exception e){throw new BusinessException("Unable to read employee xlsx: "+e.getMessage());}}
    private String cell(Row row,int index){Cell cell=row.getCell(index);if(cell==null)return "";CellType type=cell.getCellType()==CellType.FORMULA?cell.getCachedFormulaResultType():cell.getCellType();if(type==CellType.STRING)return cell.getStringCellValue().trim();if(type==CellType.NUMERIC){if(Set.of(0,2,3,10).contains(index))throw new BusinessException("Identifier fields must be stored as text (row contains a numeric value)");if(DateUtil.isCellDateFormatted(cell))return cell.getLocalDateTimeCellValue().toLocalDate().toString();String raw=cell instanceof XSSFCell x?x.getRawValue():null;return new BigDecimal(raw==null?Double.toString(cell.getNumericCellValue()):raw).stripTrailingZeros().toPlainString();}return type==CellType.BOOLEAN?Boolean.toString(cell.getBooleanCellValue()):"";}
    private <T> T validated(T request){Set<ConstraintViolation<T>> violations=validator.validate(request);if(!violations.isEmpty())throw new BusinessException(violations.iterator().next().getMessage());return request;}
    private Long id(String sql,String key,String message){List<Map<String,Object>> rows=jdbc.queryForList(sql,key);if(rows.isEmpty())throw new BusinessException(message+": "+key);return ((Number)rows.get(0).get("id")).longValue();}
}
