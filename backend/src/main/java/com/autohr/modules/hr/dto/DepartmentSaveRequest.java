package com.autohr.modules.hr.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DepartmentSaveRequest {

    private Long id;

    @NotBlank(message = "部门名称必填")
    private String departmentName;

    private String departmentCode;
    private Long parentDepartmentId;
    private Long managerEmployeeId;

    @NotBlank(message = "部门职能简介必填")
    private String description;

    private Integer sortOrder;
    private Integer status;
}
