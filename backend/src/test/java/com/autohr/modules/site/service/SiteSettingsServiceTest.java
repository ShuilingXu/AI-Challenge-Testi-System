package com.autohr.modules.site.service;

import com.autohr.modules.site.dto.SiteSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SiteSettingsServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void returnsDefaultsBeforeSettingsAreSaved() {
        SiteSettingsService service = new SiteSettingsService(
                new ObjectMapper(), tempDirectory.resolve("site-settings.json"));

        SiteSettings settings = service.get();

        assertEquals("AI School Examination System", settings.siteTitle());
        assertEquals("Class-based AI examinations and learning analytics.", settings.siteSubtitle());
        assertEquals("", settings.logoUrl());
        assertEquals("AI School Examination System", settings.footerHtml());
    }

    @Test
    void persistsNormalizedSettings() {
        SiteSettingsService service = new SiteSettingsService(
                new ObjectMapper(), tempDirectory.resolve("runtime/site-settings.json"));

        SiteSettings saved = service.save(new SiteSettings(
                " https://cdn.example.test/logo.png ",
                " Example HR ",
                " People operations ",
                " <span>Example footer</span> "));

        assertEquals("https://cdn.example.test/logo.png", saved.logoUrl());
        assertEquals("Example HR", service.get().siteTitle());
        assertEquals("People operations", service.get().siteSubtitle());
        assertEquals("<span>Example footer</span>", service.get().footerHtml());

        service.save(new SiteSettings("", "Replacement HR", "Replacement subtitle", "Replacement footer"));
        assertEquals("Replacement HR", service.get().siteTitle());
        assertEquals("Replacement footer", service.get().footerHtml());
    }

    @Test
    void rejectsUnsafeLogoUrlsAndRestoresRequiredDefaults() {
        SiteSettingsService service = new SiteSettingsService(
                new ObjectMapper(), tempDirectory.resolve("site-settings.json"));

        SiteSettings saved = service.save(new SiteSettings("javascript:alert(1)", " ", null, "<b>plain text</b>"));

        assertEquals("", saved.logoUrl());
        assertEquals("AI School Examination System", saved.siteTitle());
        assertEquals("Class-based AI examinations and learning analytics.", saved.siteSubtitle());
        assertEquals("<b>plain text</b>", saved.footerHtml());

        SiteSettings backslashPath = service.save(new SiteSettings(
                "/\\external.example/logo.png", "Example", "Subtitle", "Footer"));
        assertEquals("", backslashPath.logoUrl());
    }

    @Test
    void retainsTheLastKnownSettingsSnapshotUntilThisServiceSavesAgain() throws Exception {
        Path file = tempDirectory.resolve("site-settings.json");
        SiteSettingsService service = new SiteSettingsService(new ObjectMapper(), file);
        service.save(new SiteSettings("", "Cached HR", "Cached subtitle", "Cached footer"));

        Files.writeString(file,
                "{\"logoUrl\":\"\",\"siteTitle\":\"External write\",\"siteSubtitle\":\"External\",\"footerHtml\":\"External\"}",
                StandardCharsets.UTF_8);

        assertEquals("Cached HR", service.get().siteTitle());
        assertEquals("External write", new SiteSettingsService(new ObjectMapper(), file).get().siteTitle());
    }
}
