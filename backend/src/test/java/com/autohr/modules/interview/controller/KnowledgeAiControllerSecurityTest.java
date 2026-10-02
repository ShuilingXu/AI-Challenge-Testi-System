package com.autohr.modules.interview.controller;

import com.autohr.config.SecurityConfig;
import com.autohr.modules.auth.config.JwtAuthenticationFilter;
import com.autohr.modules.auth.config.PasswordChangeRequiredFilter;
import com.autohr.modules.auth.service.AuthService;
import com.autohr.modules.auth.service.AuditLogService;
import com.autohr.modules.interview.service.KnowledgeAiService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;

@WebMvcTest(KnowledgeAiController.class)
@Import(SecurityConfig.class)
class KnowledgeAiControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean KnowledgeAiService service;
    @MockBean AuthService auth;
    @MockBean AuditLogService audit;
    @MockBean JwtAuthenticationFilter jwt;
    @MockBean PasswordChangeRequiredFilter password;
    @BeforeEach void filters() throws Exception {
        for (var filter : List.of(jwt, password)) doAnswer(invocation -> {
            invocation.<FilterChain>getArgument(2).doFilter(invocation.<ServletRequest>getArgument(0), invocation.<ServletResponse>getArgument(1));
            return null;
        }).when(filter).doFilter(any(), any(), any());
    }
    @Test @WithMockUser(authorities = "ROLE_LECTURER")
    void teacherCanGenerateFromMaterials() throws Exception {
        when(service.generate(eq(7L), anyList())).thenReturn(List.of(new KnowledgeAiService.Item("循环", "教学内容")));
        mvc.perform(multipart("/api/interview/hr/knowledge-items/ai/generate")
                .file(new MockMultipartFile("files", "教学大纲.txt", "text/plain", "课程内容".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .param("knowledgeBaseId", "7").cookie(new Cookie("AUTOHR_CSRF", "token")).header("X-CSRF-Token", "token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].knowledgePoint").value("循环"));
    }
    @Test @WithMockUser(authorities = "ROLE_STUDENT")
    void studentCannotGenerateOrSave() throws Exception {
        mvc.perform(multipart("/api/interview/hr/knowledge-items/ai/generate")
                .file(new MockMultipartFile("files", "大纲.txt", "text/plain", new byte[]{1}))
                .param("knowledgeBaseId", "7").cookie(new Cookie("AUTOHR_CSRF", "token")).header("X-CSRF-Token", "token"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/interview/hr/knowledge-items/ai/save").contentType("application/json")
                .content("{\"knowledgeBaseId\":7,\"items\":[]}").cookie(new Cookie("AUTOHR_CSRF", "token")).header("X-CSRF-Token", "token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(authorities = "ROLE_LECTURER")
    void rejectsMissingCsrfAndInvalidDraft() throws Exception {
        mvc.perform(post("/api/interview/hr/knowledge-items/ai/save").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/interview/hr/knowledge-items/ai/save").contentType("application/json")
                .content("{\"knowledgeBaseId\":7,\"items\":[{\"knowledgePoint\":\"\",\"knowledgeContent\":\"内容\"}]}")
                .cookie(new Cookie("AUTOHR_CSRF", "token")).header("X-CSRF-Token", "token"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
