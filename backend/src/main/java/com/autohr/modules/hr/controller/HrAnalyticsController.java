package com.autohr.modules.hr.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.common.exception.BusinessException;
import com.autohr.config.database.ActiveDatabase;
import com.autohr.config.database.DatabaseType;
import com.autohr.modules.auth.dto.SessionUserVO;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.auth.service.AuditLogService;
import com.autohr.modules.hr.dto.DashboardConfigRequest;
import com.autohr.modules.hr.dto.HrStatisticsVO;
import com.autohr.modules.hr.service.HrStatisticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController @RequestMapping("/api/hr") @RequiredArgsConstructor
public class HrAnalyticsController {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String DEFAULT_CONFIG = "{\"cards\":[\"employeeCount\",\"departmentCount\",\"openJobCount\",\"newHireCount\",\"dismissalCount\",\"averageGross\"],\"charts\":{\"salary\":\"bar\",\"recruitment\":\"pie\",\"dismissal\":\"pie\",\"department\":\"bar\"}}";
    private static final Set<String> CARD_IDS = Set.of("employeeCount", "activeEmployeeCount", "departmentCount",
            "openJobCount", "newHireCount", "dismissalCount", "averageGross", "grossTotal", "salaryGrowth",
            "candidateCount", "interviewingCount", "passedCount", "dismissalAverage", "departmentAverageCount");
    private static final Set<String> CHART_KEYS = Set.of("salary", "recruitment", "dismissal", "department");
    private static final Set<String> CHART_TYPES = Set.of("bar", "pie", "table");
    private final HrStatisticsService statisticsService;
    private final AuthService authService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private ActiveDatabase activeDatabase;
    private AuditLogService auditLogService;

    @Autowired
    void configureDatabase(ActiveDatabase activeDatabase) {
        this.activeDatabase = activeDatabase;
    }

    @Autowired
    void configureAuditLog(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/statistics") public ApiResponse<HrStatisticsVO> statistics(@RequestParam(required=false) String month){return ApiResponse.success(statisticsService.statistics(month==null?YearMonth.now(BUSINESS_ZONE).toString():month));}
    @GetMapping("/dashboard/config") public ApiResponse<Map<String,Object>> config(Authentication auth){Long userId=user(auth).getId();List<Map<String,Object>> rows=jdbc.queryForList("SELECT config_json FROM user_dashboard_config WHERE user_id=?",userId);return ApiResponse.success(Map.<String,Object>of("configJson",rows.isEmpty()?DEFAULT_CONFIG:rows.get(0).get("config_json")));}
    @PostMapping("/dashboard/config") public ApiResponse<Void> saveConfig(Authentication auth,@Valid @RequestBody DashboardConfigRequest request){
        validateConfig(request.getConfigJson());
        Long userId=user(auth).getId();
        DatabaseType databaseType = activeDatabase == null ? DatabaseType.SQLITE : activeDatabase.type();
        String sql = switch (databaseType) {
            case PGSQL, SQLITE -> "INSERT INTO user_dashboard_config (user_id,config_json) VALUES (?,?) "
                    + "ON CONFLICT (user_id) DO UPDATE SET config_json=excluded.config_json";
            case MYSQL -> "INSERT INTO user_dashboard_config (user_id,config_json) VALUES (?,?) "
                    + "ON DUPLICATE KEY UPDATE config_json=VALUES(config_json)";
        };
        jdbc.update(sql, userId, request.getConfigJson());
        if (auditLogService != null) {
            SessionUserVO current = user(auth);
            auditLogService.log(current.getId(), current.getDisplayName(), current.getRoleCode(),
                    "HR", "SAVE_DASHBOARD_CONFIG", "USER_DASHBOARD_CONFIG", String.valueOf(userId), "dashboard configuration updated");
        }
        return ApiResponse.success("saved",null);
    }
    private void validateConfig(String json){try{JsonNode root=objectMapper.readTree(json);if(root==null||!root.isObject()||root.size()!=2||!root.has("cards")||!root.has("charts"))throw invalidConfig();JsonNode cards=root.get("cards");if(!cards.isArray())throw invalidConfig();for(JsonNode card:cards)if(!card.isTextual()||!CARD_IDS.contains(card.textValue()))throw invalidConfig();JsonNode charts=root.get("charts");if(!charts.isObject()||charts.size()!=CHART_KEYS.size())throw invalidConfig();for(String key:CHART_KEYS){JsonNode type=charts.get(key);if(type==null||!type.isTextual()||!CHART_TYPES.contains(type.textValue()))throw invalidConfig();}}catch(BusinessException e){throw e;}catch(Exception e){throw invalidConfig();}}
    private BusinessException invalidConfig(){return new BusinessException("Dashboard config has an invalid shape or value");}
    private SessionUserVO user(Authentication auth){return authService.loadUserByUsername(auth.getName());}
}
