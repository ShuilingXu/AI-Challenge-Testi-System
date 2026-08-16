package com.autohr.modules.hr.service.impl;

import com.autohr.config.database.ActiveDatabase;
import com.autohr.config.database.AppMigrationProperties;
import com.autohr.config.database.DatabaseMigrationRunner;
import com.autohr.config.database.DatabaseType;
import com.autohr.modules.hr.dto.EmployeeSaveRequest;
import com.autohr.modules.hr.mapper.DepartmentMapper;
import com.autohr.modules.hr.mapper.EmployeeMapper;
import com.autohr.modules.hr.mapper.IntegrationBindingMapper;
import com.autohr.modules.hr.mapper.SalaryHistoryMapper;
import com.autohr.modules.hr.service.HrStatisticsService;
import com.autohr.modules.common.config.MybatisMetaObjectHandler;
import com.autohr.modules.recruitment.mapper.RecruitmentJobMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class HrServicePersistenceIntegrationTest {

    @TempDir
    Path tempDirectory;

    @Test
    void salaryChangeWithoutEffectiveMonthPersistsHireMonthSalaryHistory() throws Exception {
        String url = "jdbc:sqlite:" + tempDirectory.resolve("hr-service.db").toString().replace('\\', '/');
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        new DatabaseMigrationRunner(dataSource, new ActiveDatabase(DatabaseType.SQLITE, url, "", "", false),
                new AppMigrationProperties()).run();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO hr_department (department_code,department_name,description,status) VALUES ('D1','Engineering','Engineering',1)");
        jdbc.update("INSERT INTO recruitment_job (job_code,job_title,department_id,department_name,requirements,responsibilities,publish_date,status) VALUES ('J1','Engineer',1,'Engineering','Requirements','Responsibilities','2026-01-01',1)");
        jdbc.update("INSERT INTO hr_employee (id,employee_code,full_name,id_card_no,mobile_phone,recruitment_major,position_name,department_id,bank_account_no,bank_name,hire_date,employment_status,job_id,base_salary,salary_confirmed,created_at,updated_at) VALUES (1,'E001','Test Employee','110101199001010011','13800000001','Computer Science','Engineer',1,'6222000000000001','Test Bank','2024-03-18',1,1,9000,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO hr_salary_history (employee_id,effective_month,base_salary_before,base_salary_after,created_at) VALUES (1,'2024-03',0,9000,CURRENT_TIMESTAMP)");

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        GlobalConfigUtils.setGlobalConfig(configuration,
                new GlobalConfig()
                        .setDbConfig(new GlobalConfig.DbConfig())
                        .setMetaObjectHandler(new MybatisMetaObjectHandler()));
        configuration.addMapper(DepartmentMapper.class);
        configuration.addMapper(EmployeeMapper.class);
        configuration.addMapper(IntegrationBindingMapper.class);
        configuration.addMapper(RecruitmentJobMapper.class);
        configuration.addMapper(SalaryHistoryMapper.class);
        SqlSessionFactory sessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);

        try (SqlSession session = sessionFactory.openSession(true)) {
            HrServiceImpl service = new HrServiceImpl(
                    session.getMapper(DepartmentMapper.class),
                    session.getMapper(EmployeeMapper.class),
                    session.getMapper(IntegrationBindingMapper.class),
                    session.getMapper(RecruitmentJobMapper.class),
                    session.getMapper(SalaryHistoryMapper.class),
                    jdbc,
                    mock(HrStatisticsService.class));
            EmployeeSaveRequest request = new EmployeeSaveRequest();
            request.setId(1L);
            request.setEmployeeCode("E001");
            request.setFullName("Test Employee");
            request.setIdCardNo("110101199001010011");
            request.setMobilePhone("13800000001");
            request.setRecruitmentMajor("Computer Science");
            request.setJobId(1L);
            request.setBaseSalary(new BigDecimal("10000.00"));
            request.setDepartmentId(1L);
            request.setBankAccountNo("6222000000000001");
            request.setBankName("Test Bank");
            Long employeeId = service.saveEmployee(request, 99L).getId();

            assertEquals("2024-03", jdbc.queryForObject(
                    "SELECT effective_month FROM hr_salary_history WHERE employee_id=?", String.class, employeeId));
            assertEquals(0, new BigDecimal("10000.00").compareTo(jdbc.queryForObject(
                    "SELECT base_salary_after FROM hr_salary_history WHERE employee_id=?", BigDecimal.class, employeeId)));
        }
    }
}
