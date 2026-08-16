package com.autohr.modules.hr.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IntegrationBindingVO {

    private Long id;
    private String moduleCode;
    private String businessType;
    private Long businessId;
    private Long employeeId;
    private String employeeName;
    private Long departmentId;
    private String departmentName;
    private String externalRef;
    private String bindingStatus;
    private String payload;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
