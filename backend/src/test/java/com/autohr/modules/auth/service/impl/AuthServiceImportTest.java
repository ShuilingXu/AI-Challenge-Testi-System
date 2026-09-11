package com.autohr.modules.auth.service.impl;

import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.entity.SysUser;
import com.autohr.modules.auth.mapper.SysUserMapper;
import com.autohr.modules.auth.service.CaptchaService;
import com.autohr.modules.auth.service.JwtService;
import com.autohr.modules.auth.service.VerificationCodeService;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImportTest {
    @Mock SysUserMapper userMapper;
    @Mock JwtService jwtService;
    @Mock VerificationCodeService verificationCodeService;
    @Mock CaptchaService captchaService;
    @Mock JdbcTemplate jdbcTemplate;

    @Test
    void templateIsLegacyXlsWithTeacherHeaders() throws Exception {
        AuthServiceImpl service = new AuthServiceImpl(userMapper, new BCryptPasswordEncoder(), jwtService,
                verificationCodeService, captchaService, jdbcTemplate);
        try (HSSFWorkbook workbook = new HSSFWorkbook(new ByteArrayInputStream(service.staffTemplate()))) {
            assertEquals("用户名", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("后台角色", workbook.getSheetAt(0).getRow(0).getCell(3).getStringCellValue());
        }
    }
}
