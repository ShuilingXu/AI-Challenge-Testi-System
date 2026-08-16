package com.autohr.modules.site.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Bounded update contract for public site content. All content fields remain optional so
 * existing records can be updated with only the fields that changed.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class SiteContentSaveRequest {

    @Min(value = 1, message = "Content id must be positive")
    private Long id;

    @Size(max = 32, message = "Content type cannot exceed 32 characters")
    private String type;

    @Size(max = 200, message = "Content title cannot exceed 200 characters")
    private String title;

    @Size(max = 500, message = "Content summary cannot exceed 500 characters")
    private String summary;

    @Size(max = 20_000, message = "Content body cannot exceed 20000 characters")
    private String content;

    @Size(max = 2_000, message = "Content cover cannot exceed 2000 characters")
    private String cover;

    private Boolean published;

    @Size(max = 64, message = "Published time cannot exceed 64 characters")
    private String publishedAt;

    @JsonIgnore
    private final Set<String> unknownFields = new LinkedHashSet<>();

    @JsonAnySetter
    public void captureUnknownField(String name, Object ignoredValue) {
        unknownFields.add(name);
    }

    public Map<String, Object> toUpdates() {
        Map<String, Object> updates = new LinkedHashMap<>();
        putIfPresent(updates, "id", id);
        putIfPresent(updates, "type", type);
        putIfPresent(updates, "title", title);
        putIfPresent(updates, "summary", summary);
        putIfPresent(updates, "content", content);
        putIfPresent(updates, "cover", cover);
        putIfPresent(updates, "published", published);
        putIfPresent(updates, "publishedAt", publishedAt);
        return updates;
    }

    private void putIfPresent(Map<String, Object> target, String name, Object value) {
        if (value != null) {
            target.put(name, value);
        }
    }
}
