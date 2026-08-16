package com.autohr.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_audit_log")
public class SysAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operatorUserId;
    private String operatorUsername;
    private String operatorRoleCode;
    private String moduleCode;
    private String actionCode;
    private String targetType;
    private String targetId;
    private String detail;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
