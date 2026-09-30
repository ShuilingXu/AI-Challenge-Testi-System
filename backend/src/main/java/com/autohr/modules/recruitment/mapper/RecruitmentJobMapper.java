package com.autohr.modules.recruitment.mapper;

import com.autohr.modules.recruitment.entity.RecruitmentJob;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface RecruitmentJobMapper extends BaseMapper<RecruitmentJob> {

    @Select("SELECT id, job_code AS jobCode, job_title AS jobTitle, department_name AS departmentName, "
            + "requirements, responsibilities, publish_date AS publishDate, status, created_at AS createdAt, updated_at AS updatedAt "
            + "FROM school_assessment_config WHERE id=#{id}")
    RecruitmentJob selectSchoolJobById(Long id);

    @Insert("INSERT INTO school_assessment_config "
            + "(job_code, job_title, department_name, requirements, responsibilities, publish_date, status) "
            + "VALUES (#{jobCode}, #{jobTitle}, #{departmentName}, #{requirements}, #{responsibilities}, #{publishDate}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertSchoolJob(RecruitmentJob job);

    @Update("UPDATE school_assessment_config SET job_code=#{jobCode}, job_title=#{jobTitle}, department_name=#{departmentName}, "
            + "requirements=#{requirements}, responsibilities=#{responsibilities}, status=#{status}, updated_at=CURRENT_TIMESTAMP "
            + "WHERE id=#{id}")
    int updateSchoolJob(RecruitmentJob job);
}
