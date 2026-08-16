package com.autohr.modules.system.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemConfigServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void savesValuesUsingTheSamePropertiesSyntaxThatSpringLoads() throws Exception {
        Path envPath = tempDirectory.resolve(".env");
        Files.writeString(envPath, "# keep this comment\nUNCHANGED=value\n", StandardCharsets.UTF_8);
        SystemConfigService service = new SystemConfigService(envPath);
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("SMTP_PASSWORD", " ~!@Pass word#1\\tail ");
        updates.put("S3_SESSION_TOKEN", "quote\"'=:!#$value");

        service.saveConfig(updates);

        assertEquals(updates, service.loadConfig("SMTP_PASSWORD", "S3_SESSION_TOKEN"));
        assertTrue(Files.readString(envPath, StandardCharsets.UTF_8).contains("# keep this comment"));
        Properties springProperties = new Properties();
        try (Reader reader = Files.newBufferedReader(envPath, StandardCharsets.UTF_8)) {
            springProperties.load(reader);
        }
        assertEquals(updates.get("SMTP_PASSWORD"), springProperties.getProperty("SMTP_PASSWORD"));
        assertEquals(updates.get("S3_SESSION_TOKEN"), springProperties.getProperty("S3_SESSION_TOKEN"));
        assertEquals("value", springProperties.getProperty("UNCHANGED"));
    }

    @Test
    void processEnvironmentOverridesTheEnvFile() throws Exception {
        Path envPath = tempDirectory.resolve("priority.env");
        Files.writeString(envPath, "SMTP_HOST=file.example.com\nSMTP_PORT=587\n", StandardCharsets.UTF_8);
        Map<String, String> processEnvironment = Map.of("SMTP_HOST", "runtime.example.com");
        SystemConfigService service = new SystemConfigService(envPath, processEnvironment::get);

        Map<String, String> config = service.loadConfig("SMTP_HOST", "SMTP_PORT");

        assertEquals("runtime.example.com", config.get("SMTP_HOST"));
        assertEquals("587", config.get("SMTP_PORT"));
    }
}
