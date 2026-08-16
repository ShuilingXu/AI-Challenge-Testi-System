package com.autohr.modules.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SchoolStudentSaveRequest {
    private Long id;

    @NotBlank(message = "学号不能为空")
    @Size(max = 64)
    private String studentNo;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64)
    private String fullName;

    @NotNull(message = "请选择班级")
    @Positive(message = "班级无效")
    private Long classId;
    private Integer status;
}
