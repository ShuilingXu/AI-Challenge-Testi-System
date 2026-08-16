package com.autohr.modules.site.service;

import com.autohr.modules.site.dto.SiteSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class SiteSettingsService {

    private static final SiteSettings DEFAULT_SETTINGS = new SiteSettings(
            "", "AI School Examination System",
            "Class-based AI examinations and learning analytics.",
            "AI School Examination System");

    private final ObjectMapper objectMapper;
    private final Path settingsPath;
    /** The settings file changes only through this service, so a volatile snapshot is sufficient. */
    private volatile SiteSettings cachedSettings;

    @Autowired
    public SiteSettingsService(ObjectMapper objectMapper,
                               @Value("${site-settings.path:.site-settings.json}") String settingsPath) {
        this(objectMapper, Paths.get(settingsPath));
    }

    SiteSettingsService(ObjectMapper objectMapper, Path settingsPath) {
        this.objectMapper = objectMapper;
        this.settingsPath = settingsPath;
    }

    public synchronized SiteSettings get() {
        SiteSettings snapshot = cachedSettings;
        if (snapshot != null) {
            return snapshot;
        }
        if (!Files.exists(settingsPath)) {
            cachedSettings = DEFAULT_SETTINGS;
            return cachedSettings;
        }
        try {
            cachedSettings = normalize(objectMapper.readValue(
                    Files.readString(settingsPath, StandardCharsets.UTF_8), SiteSettings.class));
            return cachedSettings;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read site settings", ex);
        }
    }

    public synchronized SiteSettings save(SiteSettings incoming) {
        SiteSettings settings = normalize(incoming);
        Path absolutePath = settingsPath.toAbsolutePath();
        Path parent = absolutePath.getParent();
        Path temporaryPath = null;
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            temporaryPath = Files.createTempFile(parent, ".site-settings-", ".tmp");
            Files.writeString(temporaryPath,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(settings),
                    StandardCharsets.UTF_8);
            try {
                Files.move(temporaryPath, absolutePath,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporaryPath, absolutePath, StandardCopyOption.REPLACE_EXISTING);
            }
            cachedSettings = settings;
            return settings;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save site settings", ex);
        } finally {
            if (temporaryPath != null) {
                try {
                    Files.deleteIfExists(temporaryPath);
                } catch (IOException ignored) {
                    // Runtime cleanup can remove an abandoned temporary file later.
                }
            }
        }
    }

    private SiteSettings normalize(SiteSettings settings) {
        if (settings == null) {
            return DEFAULT_SETTINGS;
        }
        return new SiteSettings(
                cleanLogoUrl(settings.logoUrl()),
                cleanRequired(settings.siteTitle(), DEFAULT_SETTINGS.siteTitle()),
                cleanRequired(settings.siteSubtitle(), DEFAULT_SETTINGS.siteSubtitle()),
                cleanOptional(settings.footerHtml()));
    }

    private String cleanLogoUrl(String value) {
        String cleaned = cleanOptional(value);
        if (cleaned.startsWith("/") && !cleaned.startsWith("//") && !cleaned.contains("\\")) {
            return cleaned;
        }
        try {
            return "https".equalsIgnoreCase(java.net.URI.create(cleaned).getScheme()) ? cleaned : "";
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private String cleanRequired(String value, String fallback) {
        String cleaned = cleanOptional(value);
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private String cleanOptional(String value) {
        return value == null ? "" : value.trim();
    }
}
