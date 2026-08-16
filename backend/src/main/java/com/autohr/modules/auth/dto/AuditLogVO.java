package com.autohr.modules.auth.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditLogVO {
    private Long id;
    private Long operatorUserId;
    private String operatorUsername;
    private String operatorRoleCode;
    private String moduleCode;
    private String actionCode;
    private String targetType;
    private String targetId;
    private String detail;
    private LocalDateTime createdAt;
}
