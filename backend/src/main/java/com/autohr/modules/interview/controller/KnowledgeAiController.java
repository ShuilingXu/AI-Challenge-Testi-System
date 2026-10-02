package com.autohr.modules.interview.controller;

import com.autohr.common.api.ApiResponse;
import com.autohr.modules.interview.service.KnowledgeAiService;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.auth.service.AuditLogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview/hr/knowledge-items/ai")
@RequiredArgsConstructor
public class KnowledgeAiController {
    private final KnowledgeAiService service;
    private final AuthService auth;
    private final AuditLogService audit;
    public record SaveRequest(@NotNull Long knowledgeBaseId,
                              @Valid @NotNull @Size(min = 1, max = 100) List<KnowledgeAiService.Item> items) {}

    @PostMapping(value = "/generate", consumes = "multipart/form-data")
    public ApiResponse<List<KnowledgeAiService.Item>> generate(@RequestParam Long knowledgeBaseId,
                                                              @RequestParam("files") List<MultipartFile> files) {
        return ApiResponse.success(service.generate(knowledgeBaseId, files));
    }

    @PostMapping("/save")
    @Transactional
    public ApiResponse<Map<String, Integer>> save(Authentication authentication, @Valid @RequestBody SaveRequest request) {
        int count = service.save(request.knowledgeBaseId(), request.items());
        var user = auth.loadUserByUsername(authentication.getName());
        audit.log(user.getId(), user.getDisplayName(), user.getRoleCode(), "INTERVIEW", "AI_IMPORT_KNOWLEDGE_ITEMS",
                "KNOWLEDGE_BASE", String.valueOf(request.knowledgeBaseId()), "imported=" + count);
        return ApiResponse.success(Map.of("imported", count));
    }
}
