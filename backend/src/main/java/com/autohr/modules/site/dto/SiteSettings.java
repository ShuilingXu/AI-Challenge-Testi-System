package com.autohr.modules.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SiteSettings(
        @NotNull(message = "Logo URL is required")
        @Size(max = 500, message = "Logo URL cannot exceed 500 characters") String logoUrl,
        @NotBlank(message = "Site title is required")
        @Size(max = 120, message = "Site title cannot exceed 120 characters") String siteTitle,
        @NotBlank(message = "Site subtitle is required")
        @Size(max = 500, message = "Site subtitle cannot exceed 500 characters") String siteSubtitle,
        @NotNull(message = "Footer text is required")
        @Size(max = 500, message = "Footer text cannot exceed 500 characters") String footerHtml) {
}
