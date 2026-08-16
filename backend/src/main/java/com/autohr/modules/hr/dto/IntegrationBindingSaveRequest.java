package com.autohr.modules.hr.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IntegrationBindingSaveRequest {

    private Long id;

    @NotBlank(message = "模块编码必填")
    private String moduleCode;

    @NotBlank(message = "业务类型必填")
    private String businessType;

    private Long businessId;
    private Long employeeId;
    private Long departmentId;
    private String externalRef;
    private String bindingStatus;
    private String payload;
}
