package com.autohr.modules.auth.service;

import com.autohr.common.api.PageQuery;
import com.autohr.common.api.PageResponse;
import com.autohr.modules.auth.dto.AuditLogVO;

public interface AuditLogService {
    void log(Long operatorUserId, String operatorUsername, String operatorRoleCode, String moduleCode, String actionCode, String targetType, String targetId, String detail);
    PageResponse<AuditLogVO> list(String moduleCode, String actionCode, String keyword, PageQuery pageQuery);
}
