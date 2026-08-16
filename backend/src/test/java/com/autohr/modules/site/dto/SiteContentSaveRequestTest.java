package com.autohr.modules.site.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteContentSaveRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void capturesUnknownFieldsInsteadOfPersistingThem() throws Exception {
        SiteContentSaveRequest request = new ObjectMapper().readValue(
                "{\"title\":\"Announcement\",\"debug\":true}", SiteContentSaveRequest.class);

        assertTrue(request.getUnknownFields().contains("debug"));
        assertFalse(request.toUpdates().containsKey("debug"));
        assertEquals("Announcement", request.toUpdates().get("title"));
    }

    @Test
    void rejectsOversizedContentFields() {
        SiteContentSaveRequest request = new SiteContentSaveRequest();
        request.setTitle("x".repeat(201));
        request.setSummary("x".repeat(501));
        request.setContent("x".repeat(20_001));

        assertFalse(validator.validate(request).isEmpty());
    }
}
