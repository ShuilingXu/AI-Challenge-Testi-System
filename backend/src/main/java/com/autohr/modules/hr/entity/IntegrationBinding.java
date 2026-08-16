package com.autohr.modules.hr.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("hr_integration_binding")
public class IntegrationBinding {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String moduleCode;
    private String businessType;
    private Long businessId;
    private Long employeeId;
    private Long departmentId;
    private String externalRef;
    private String bindingStatus;
    private String payload;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
