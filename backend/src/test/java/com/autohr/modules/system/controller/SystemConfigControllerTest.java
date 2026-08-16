package com.autohr.modules.system.controller;

import com.autohr.common.exception.BusinessException;
import com.autohr.modules.system.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemConfigControllerTest {

    @Mock
    SystemConfigService systemConfigService;

    @Test
    void requiresInternalEndpointWhenInternalS3UploadIsEnabled() {
        when(systemConfigService.loadConfig(any(String[].class))).thenReturn(new LinkedHashMap<>());
        SystemConfigController controller = new SystemConfigController(systemConfigService);
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("S3_ENABLED", "true");
        updates.put("S3_ENDPOINT", "https://1.1.1.1");
        updates.put("S3_INTERNAL_ENDPOINT_ENABLED", "true");
        updates.put("S3_INTERNAL_ENDPOINT", "");
        updates.put("S3_REGION", "us-east-1");
        updates.put("S3_BUCKET", "autohr");
        updates.put("S3_ACCESS_KEY_ID", "access-key");
        updates.put("S3_SECRET_ACCESS_KEY", "secret-key");

        assertThrows(BusinessException.class, () -> controller.saveConfig(updates));

        verify(systemConfigService, never()).saveConfig(any());
    }

    @Test
    void rejectsS3EndpointWithEmbeddedCredentialsOrFragment() {
        when(systemConfigService.loadConfig(any(String[].class))).thenReturn(new LinkedHashMap<>());
        SystemConfigController controller = new SystemConfigController(systemConfigService);
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("S3_ENABLED", "true");
        updates.put("S3_ENDPOINT", "https://user:secret@1.1.1.1/#credential");
        updates.put("S3_REGION", "us-east-1");
        updates.put("S3_BUCKET", "autohr");
        updates.put("S3_ACCESS_KEY_ID", "access-key");
        updates.put("S3_SECRET_ACCESS_KEY", "secret-key");

        assertThrows(BusinessException.class, () -> controller.saveConfig(updates));
        verify(systemConfigService, never()).saveConfig(any());
    }

    @Test
    void rejectsHttpAndPrivateS3EndpointsByDefault() {
        when(systemConfigService.loadConfig(any(String[].class))).thenReturn(new LinkedHashMap<>());
        SystemConfigController controller = new SystemConfigController(systemConfigService);
        Map<String, String> updates = completeS3Config("http://127.0.0.1:9000");

        assertThrows(BusinessException.class, () -> controller.saveConfig(updates));

        verify(systemConfigService, never()).saveConfig(any());
    }

    @Test
    void permitsTrustedMinioOnlyWithExplicitCompatibilitySwitches() {
        when(systemConfigService.loadConfig(any(String[].class))).thenReturn(new LinkedHashMap<>());
        SystemConfigController controller = new SystemConfigController(systemConfigService);
        Map<String, String> updates = completeS3Config("http://127.0.0.1:9000");
        updates.put("S3_ALLOW_HTTP_ENDPOINTS", "true");
        updates.put("S3_ALLOW_PRIVATE_ENDPOINTS", "true");

        controller.saveConfig(updates);

        verify(systemConfigService).saveConfig(updates);
    }

    private Map<String, String> completeS3Config(String endpoint) {
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("S3_ENABLED", "true");
        updates.put("S3_ENDPOINT", endpoint);
        updates.put("S3_REGION", "us-east-1");
        updates.put("S3_BUCKET", "autohr");
        updates.put("S3_ACCESS_KEY_ID", "access-key");
        updates.put("S3_SECRET_ACCESS_KEY", "secret-key");
        return updates;
    }
}
