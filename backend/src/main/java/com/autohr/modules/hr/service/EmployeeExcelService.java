package com.autohr.modules.hr.service;
import com.autohr.modules.hr.dto.ImportResultVO;
import org.springframework.web.multipart.MultipartFile;
public interface EmployeeExcelService { byte[] template(); ImportResultVO importEmployees(MultipartFile file, Long operatorUserId); }
