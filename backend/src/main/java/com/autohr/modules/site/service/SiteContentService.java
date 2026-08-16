package com.autohr.modules.site.service;

import com.autohr.common.api.PageQuery;
import com.autohr.common.api.PageResponse;
import com.autohr.common.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Small file-backed store for the public information pages. Content is independent from .env. */
@Service
public class SiteContentService {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "id", "type", "title", "summary", "content", "cover", "published", "publishedAt");

    private final ObjectMapper objectMapper;
    private final Path contentPath;
    /** Cached snapshots avoid disk I/O for every public page request. */
    private volatile List<Map<String, Object>> cachedItems;

    @Autowired
    public SiteContentService(ObjectMapper objectMapper,
                              @Value("${site-content.path:.site-content.json}") String contentPath) {
        this(objectMapper, Paths.get(contentPath));
    }

    SiteContentService(ObjectMapper objectMapper, Path contentPath) {
        this.objectMapper = objectMapper;
        this.contentPath = contentPath;
    }

    public synchronized PageResponse<Map<String, Object>> list(boolean publishedOnly, PageQuery pageQuery) {
        List<Map<String, Object>> items = read();
        List<Map<String, Object>> filteredItems = items.stream()
                .filter(item -> !publishedOnly || Boolean.TRUE.equals(item.get("published")))
                .sorted(Comparator.comparing(item -> String.valueOf(item.getOrDefault("publishedAt", "")), Comparator.reverseOrder()))
                .toList();
        return PageResponse.slice(filteredItems, pageQuery);
    }

    public synchronized Map<String, Object> save(Map<String, Object> incoming) {
        List<Map<String, Object>> items = read();
        Map<String, Object> updates = incoming == null ? Map.of() : incoming;
        validateAllowedFields(updates);
        long id = number(updates.get("id"));
        long requestedId = id;
        Map<String, Object> existingItem = id == 0 ? null : items.stream()
                .filter(item -> number(item.get("id")) == requestedId)
                .findFirst()
                .orElse(null);
        if (id != 0 && existingItem == null) {
            throw new BusinessException("Site content does not exist. Create it without an id.");
        }
        Map<String, Object> item = new LinkedHashMap<>();
        if (existingItem != null) {
            item.putAll(existingItem);
        }
        item.putAll(updates);
        if (id == 0) {
            id = items.stream().mapToLong(existing -> number(existing.get("id"))).max().orElse(0) + 1;
        }
        item.put("id", id);
        item.putIfAbsent("type", "announcement");
        item.putIfAbsent("published", Boolean.FALSE);
        item.putIfAbsent("publishedAt", "");
        item.putIfAbsent("summary", "");
        item.putIfAbsent("content", "");
        item.putIfAbsent("cover", "");
        final long savedId = id;
        items.removeIf(existing -> number(existing.get("id")) == savedId);
        items.add(item);
        write(items);
        return item;
    }

    public synchronized void delete(long id) {
        List<Map<String, Object>> items = read();
        items.removeIf(item -> number(item.get("id")) == id);
        write(items);
    }

    private List<Map<String, Object>> read() {
        List<Map<String, Object>> snapshot = cachedItems;
        if (snapshot == null) {
            snapshot = immutableSnapshot(loadFromDisk());
            cachedItems = snapshot;
        }
        return mutableCopy(snapshot);
    }

    private List<Map<String, Object>> loadFromDisk() {
        if (!Files.exists(contentPath)) {
            return defaultContent();
        }
        try {
            return objectMapper.readValue(Files.readString(contentPath, StandardCharsets.UTF_8), new TypeReference<>() {});
        } catch (IOException ex) {
            throw new IllegalStateException("读取站点内容失败", ex);
        }
    }

    private void write(List<Map<String, Object>> items) {
        Path absolutePath = contentPath.toAbsolutePath();
        Path parent = absolutePath.getParent();
        Path temporaryDirectory = parent == null ? Paths.get(".").toAbsolutePath() : parent;
        Path temporaryPath = null;
        try {
            Files.createDirectories(temporaryDirectory);
            temporaryPath = Files.createTempFile(temporaryDirectory, ".site-content-", ".tmp");
            Files.writeString(temporaryPath,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(items),
                    StandardCharsets.UTF_8);
            try {
                Files.move(temporaryPath, absolutePath,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporaryPath, absolutePath, StandardCopyOption.REPLACE_EXISTING);
            }
            // Invalidate only after the new file is safely committed.
            cachedItems = null;
        } catch (IOException ex) {
            throw new IllegalStateException("保存站点内容失败", ex);
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

    private List<Map<String, Object>> immutableSnapshot(List<Map<String, Object>> items) {
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (Map<String, Object> item : items) {
            snapshot.add(Collections.unmodifiableMap(new LinkedHashMap<>(item)));
        }
        return Collections.unmodifiableList(snapshot);
    }

    private List<Map<String, Object>> mutableCopy(List<Map<String, Object>> items) {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> item : items) {
            copy.add(new LinkedHashMap<>(item));
        }
        return copy;
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return 0; }
    }

    private void validateAllowedFields(Map<String, Object> updates) {
        List<String> unknown = updates.keySet().stream()
                .filter(name -> !ALLOWED_FIELDS.contains(name))
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            throw new BusinessException("Unsupported site content fields: " + String.join(", ", unknown));
        }
    }

    private List<Map<String, Object>> defaultContent() {
        Map<String, Object> welcome = new LinkedHashMap<>();
        welcome.put("id", 1L);
        welcome.put("type", "announcement");
        welcome.put("title", "学校考试通知");
        welcome.put("summary", "查看考试安排、知识点范围与学习提醒");
        welcome.put("content", "这里是学校考试信息与最新通知的统一入口。教师可以在后台编辑和发布内容。");
        welcome.put("cover", "");
        welcome.put("published", true);
        welcome.put("publishedAt", "2026-08-13 09:00");
        return new ArrayList<>(List.of(welcome));
    }
}
