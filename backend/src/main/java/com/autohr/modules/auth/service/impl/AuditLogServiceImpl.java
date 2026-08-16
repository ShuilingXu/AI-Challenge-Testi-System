package com.autohr.modules.auth.service.impl;

import com.autohr.common.api.PageQuery;
import com.autohr.common.api.PageResponse;
import com.autohr.modules.auth.dto.AuditLogVO;
import com.autohr.modules.auth.entity.SysAuditLog;
import com.autohr.modules.auth.mapper.SysAuditLogMapper;
import com.autohr.modules.auth.service.AuditLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final SysAuditLogMapper auditLogMapper;

    @Override
    public synchronized void log(Long operatorUserId, String operatorUsername, String operatorRoleCode, String moduleCode, String actionCode, String targetType, String targetId, String detail) {
        SysAuditLog log = new SysAuditLog();
        log.setOperatorUserId(operatorUserId);
        log.setOperatorUsername(operatorUsername);
        log.setOperatorRoleCode(operatorRoleCode);
        log.setModuleCode(moduleCode);
        log.setActionCode(actionCode);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        auditLogMapper.insert(log);
    }

    @Override
    public PageResponse<AuditLogVO> list(String moduleCode, String actionCode, String keyword, PageQuery pageQuery) {
        boolean adminCategory = "ADMIN".equals(moduleCode);
        Page<SysAuditLog> result = auditLogMapper.selectPage(new Page<>(pageQuery.page(), pageQuery.pageSize()),
                new LambdaQueryWrapper<SysAuditLog>()
                .eq(moduleCode != null && !moduleCode.isBlank() && !adminCategory, SysAuditLog::getModuleCode, moduleCode)
                .notIn(adminCategory, SysAuditLog::getModuleCode, "INTERVIEW", "RECRUITMENT")
                .eq(actionCode != null && !actionCode.isBlank(), SysAuditLog::getActionCode, actionCode)
                .and(keyword != null && !keyword.isBlank(), q -> q.like(SysAuditLog::getOperatorUsername, keyword)
                        .or().like(SysAuditLog::getDetail, keyword)
                        .or().like(SysAuditLog::getTargetId, keyword))
                .orderByDesc(SysAuditLog::getId));
        return PageResponse.of(result.getRecords().stream().map(this::toVO).toList(), result.getTotal(), pageQuery);
    }

    private AuditLogVO toVO(SysAuditLog log) {
        AuditLogVO vo = new AuditLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }

}
