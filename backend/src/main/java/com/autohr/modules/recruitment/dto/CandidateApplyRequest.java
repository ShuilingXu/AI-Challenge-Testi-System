package com.autohr.modules.recruitment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CandidateApplyRequest {

    @NotNull(message = "招聘岗位必填")
    private Long jobId;

    @NotBlank(message = "姓名必填")
    @Size(max = 64, message = "姓名不能超过64个字符")
    private String fullName;

    @NotBlank(message = "手机号必填")
    @Size(max = 32, message = "手机号不能超过32个字符")
    private String mobilePhone;

    @Size(max = 128, message = "邮箱不能超过128个字符")
    private String email;
    @Size(max = 32, message = "身份证号不能超过32个字符")
    private String idCardNo;

    @NotBlank(message = "专业必填")
    @Size(max = 128, message = "专业不能超过128个字符")
    private String major;

    @Size(max = 64, message = "学历不能超过64个字符")
    private String educationLevel;
    @Size(max = 128, message = "毕业院校不能超过128个字符")
    private String graduationSchool;
    private Integer yearsOfExperience;
    @Size(max = 128, message = "期望薪资不能超过128个字符")
    private String expectedSalary;
    @Size(max = 2000, message = "自我介绍不能超过2000个字符")
    private String selfIntroduction;
}
