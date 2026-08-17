package com.autohr.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentFileBootstrapTest {

    @TempDir
    Path tempDirectory;

    @Test
    void createsEnvFromTemplateAndGeneratesMissingJwtSecret() throws Exception {
        Files.writeString(tempDirectory.resolve(".env.example"), "JWT_SECRET=\nSCHOOL_LLM_MODEL=example-model\n", StandardCharsets.UTF_8);

        Path envPath = EnvironmentFileBootstrap.ensureEnvFile(tempDirectory);

        Properties values = new Properties();
        try (var reader = Files.newBufferedReader(envPath, StandardCharsets.UTF_8)) {
            values.load(reader);
        }
        assertEquals("example-model", values.getProperty("SCHOOL_LLM_MODEL"));
        assertFalse(values.getProperty("JWT_SECRET").isBlank());
        assertTrue(values.getProperty("JWT_SECRET").length() >= 48);
    }

    @Test
    void preservesAnExistingEnvironmentFileAndSecret() throws Exception {
        Path envPath = tempDirectory.resolve(".env");
        Files.writeString(envPath, "JWT_SECRET=existing-secret\nSCHOOL_LLM_MODEL=current-model\n", StandardCharsets.UTF_8);

        EnvironmentFileBootstrap.ensureEnvFile(tempDirectory);

        String content = Files.readString(envPath, StandardCharsets.UTF_8);
        assertTrue(content.contains("JWT_SECRET=existing-secret"));
        assertTrue(content.contains("SCHOOL_LLM_MODEL=current-model"));
    }
}
