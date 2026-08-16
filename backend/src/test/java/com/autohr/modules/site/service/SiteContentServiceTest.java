package com.autohr.modules.site.service;

import com.autohr.common.api.PageQuery;
import com.autohr.common.api.PageResponse;
import com.autohr.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteContentServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void partialSavePreservesFieldsThatWereNotProvided() {
        SiteContentService service = new SiteContentService(new ObjectMapper(), tempDirectory.resolve("content.json"));
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("title", "Original title");
        original.put("summary", "Original summary");
        original.put("content", "Original content");
        original.put("published", true);
        original.put("publishedAt", "2026-08-15 09:00");
        original.put("cover", "cover.png");
        Map<String, Object> created = service.save(original);

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("id", created.get("id"));
        update.put("title", "Updated title");
        Map<String, Object> saved = service.save(update);

        assertEquals("Updated title", saved.get("title"));
        assertEquals("Original summary", saved.get("summary"));
        assertEquals("Original content", saved.get("content"));
        assertEquals(true, saved.get("published"));
        assertEquals("2026-08-15 09:00", saved.get("publishedAt"));
        assertEquals("cover.png", saved.get("cover"));
    }

    @Test
    void suppliedFieldsOverwriteExistingValuesAndListsUseThePageContract() {
        SiteContentService service = new SiteContentService(new ObjectMapper(), tempDirectory.resolve("content.json"));
        Map<String, Object> created = service.save(new LinkedHashMap<>(Map.of(
                "title", "Draft", "summary", "Summary", "published", true, "publishedAt", "2026-08-15 09:00")));
        Map<String, Object> saved = service.save(new LinkedHashMap<>(Map.of(
                "id", created.get("id"), "published", false)));

        assertFalse((Boolean) saved.get("published"));
        PageResponse<Map<String, Object>> page = service.list(false, PageQuery.of(1, 1));
        assertEquals(2, page.getTotal());
        assertEquals(1, page.getPage());
        assertEquals(1, page.getPageSize());
        assertEquals(1, page.getItems().size());
        assertEquals(1, service.list(true, PageQuery.of(1, 20)).getItems().size());
    }

    @Test
    void rejectsAnUpdateForAnUnknownContentId() {
        SiteContentService service = new SiteContentService(new ObjectMapper(), tempDirectory.resolve("content.json"));

        assertThrows(BusinessException.class, () -> service.save(Map.of("id", 999L, "title", "Unknown")));
    }

    @Test
    void rejectsFieldsOutsideThePublicContentContract() {
        SiteContentService service = new SiteContentService(new ObjectMapper(), tempDirectory.resolve("content.json"));

        assertThrows(BusinessException.class, () -> service.save(Map.of("debug", true)));
    }

    @Test
    void createsParentDirectoriesAndLeavesNoPartialTemporaryFile() throws Exception {
        Path contentPath = tempDirectory.resolve("site-data/content.json");
        SiteContentService service = new SiteContentService(new ObjectMapper(), contentPath);

        service.save(Map.of("title", "Atomic content"));

        assertTrue(Files.exists(contentPath));
        assertTrue(Files.isDirectory(contentPath.getParent()));
        try (Stream<Path> files = Files.list(contentPath.getParent())) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".site-content-")));
        }
        assertTrue(Files.readString(contentPath, StandardCharsets.UTF_8).contains("Atomic content"));
    }

    @Test
    void usesCachedSnapshotUntilAWriteInvalidatesIt() throws Exception {
        Path contentPath = tempDirectory.resolve("content.json");
        SiteContentService service = new SiteContentService(new ObjectMapper(), contentPath);
        Map<String, Object> created = service.save(Map.of("title", "Cached content"));
        assertEquals("Cached content", service.list(false, PageQuery.of(1, 20)).getItems().get(1).get("title"));

        Files.writeString(contentPath, "not-json", StandardCharsets.UTF_8);
        // A public read does not repeatedly hit a file that another process is replacing.
        assertEquals("Cached content", service.list(false, PageQuery.of(1, 20)).getItems().get(1).get("title"));

        service.save(Map.of("id", created.get("id"), "title", "Fresh content"));
        assertEquals("Fresh content", service.list(false, PageQuery.of(1, 20)).getItems().get(1).get("title"));
    }
}
