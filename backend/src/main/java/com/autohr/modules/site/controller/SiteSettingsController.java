package com.autohr.modules.site.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.modules.site.dto.SiteSettings;
import com.autohr.modules.site.service.SiteSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/site-settings")
@RequiredArgsConstructor
public class SiteSettingsController {

    private final SiteSettingsService siteSettingsService;

    @GetMapping
    public ApiResponse<SiteSettings> publicSettings() {
        return ApiResponse.success(siteSettingsService.get());
    }

    @GetMapping("/admin")
    public ApiResponse<SiteSettings> adminSettings() {
        return ApiResponse.success(siteSettingsService.get());
    }

    @PostMapping("/admin")
    public ApiResponse<SiteSettings> save(@Valid @RequestBody SiteSettings settings) {
        return ApiResponse.success(siteSettingsService.save(settings));
    }
}
