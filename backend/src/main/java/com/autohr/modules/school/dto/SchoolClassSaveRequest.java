package com.autohr.modules.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SchoolClassSaveRequest {
    private Long id;

    @NotBlank(message = "专业不能为空")
    @Size(max = 128)
    private String majorName;

    @NotBlank(message = "班级名称不能为空")
    @Size(max = 128)
    private String className;

    @NotBlank(message = "班级代码不能为空")
    @Size(max = 64)
    private String classCode;

    @Size(max = 1000)
    private String description;
    private Integer status;
}
