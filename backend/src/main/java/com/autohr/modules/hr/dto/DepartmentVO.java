package com.autohr.modules.hr.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DepartmentVO {

    private Long id;
    private String departmentName;
    private String departmentCode;
    private Long parentDepartmentId;
    private String parentDepartmentName;
    private Long managerEmployeeId;
    private String managerEmployeeName;
    private String description;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
