package com.autohr.modules.site.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.common.api.PageQuery;
import com.autohr.common.api.PageResponse;
import com.autohr.common.exception.BusinessException;
import com.autohr.modules.site.dto.SiteContentSaveRequest;
import com.autohr.modules.site.service.SiteContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/site-content")
@RequiredArgsConstructor
public class SiteContentController {

    private final SiteContentService siteContentService;

    @GetMapping
    public ApiResponse<PageResponse<Map<String, Object>>> published(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(siteContentService.list(true, PageQuery.of(page, pageSize)));
    }

    @GetMapping("/admin")
    public ApiResponse<PageResponse<Map<String, Object>>> all(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(siteContentService.list(false, PageQuery.of(page, pageSize)));
    }

    @PostMapping("/admin")
    public ApiResponse<Map<String, Object>> save(@Valid @RequestBody SiteContentSaveRequest content) {
        if (!content.getUnknownFields().isEmpty()) {
            throw new BusinessException("Unsupported site content fields: "
                    + String.join(", ", content.getUnknownFields()));
        }
        return ApiResponse.success(siteContentService.save(content.toUpdates()));
    }

    @DeleteMapping("/admin/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        siteContentService.delete(id);
        return ApiResponse.success(null);
    }
}
