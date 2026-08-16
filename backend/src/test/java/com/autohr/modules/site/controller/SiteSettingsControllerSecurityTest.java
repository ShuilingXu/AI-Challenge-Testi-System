package com.autohr.modules.site.controller;

import com.autohr.config.SecurityConfig;
import com.autohr.modules.auth.config.JwtAuthenticationFilter;
import com.autohr.modules.auth.config.PasswordChangeRequiredFilter;
import com.autohr.modules.site.dto.SiteSettings;
import com.autohr.modules.site.service.SiteSettingsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SiteSettingsController.class)
@Import(SecurityConfig.class)
class SiteSettingsControllerSecurityTest {

    private static final SiteSettings SETTINGS = new SiteSettings(
            "", "Example HR", "People operations", "Example footer");

    @Autowired
    MockMvc mockMvc;

    @MockBean
    SiteSettingsService siteSettingsService;

    @MockBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    PasswordChangeRequiredFilter passwordChangeRequiredFilter;

    @BeforeEach
    void continueThroughAuthenticationFilters() throws Exception {
        doAnswer(invocation -> {
            invocation.<FilterChain>getArgument(2).doFilter(
                    invocation.<ServletRequest>getArgument(0),
                    invocation.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
        doAnswer(invocation -> {
            invocation.<FilterChain>getArgument(2).doFilter(
                    invocation.<ServletRequest>getArgument(0),
                    invocation.<ServletResponse>getArgument(1));
            return null;
        }).when(passwordChangeRequiredFilter).doFilter(any(), any(), any());
        when(siteSettingsService.get()).thenReturn(SETTINGS);
        when(siteSettingsService.save(any(SiteSettings.class))).thenReturn(SETTINGS);
    }

    @Test
    void publicSettingsAreAnonymousAndCarryTheCspHeader() throws Exception {
        mockMvc.perform(get("/api/site-settings"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("script-src 'self'")))
                .andExpect(jsonPath("$.data.siteTitle").value("Example HR"));
    }

    @Test
    void anonymousUsersCannotReadAdminSettings() throws Exception {
        mockMvc.perform(get("/api/site-settings/admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_HR_ADMIN")
    void hrAdministratorsCannotChangeSiteSettings() throws Exception {
        mockMvc.perform(post("/api/site-settings/admin")
                        .cookie(new Cookie("AUTOHR_CSRF", "test-csrf-token"))
                        .header("X-CSRF-Token", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logoUrl\":\"\",\"siteTitle\":\"Example HR\","
                                + "\"siteSubtitle\":\"People operations\",\"footerHtml\":\"Footer\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_IT_ADMIN")
    void itAdministratorsCanReadAndSaveSettings() throws Exception {
        mockMvc.perform(get("/api/site-settings/admin"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/site-settings/admin")
                        .cookie(new Cookie("AUTOHR_CSRF", "test-csrf-token"))
                        .header("X-CSRF-Token", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logoUrl\":\"\",\"siteTitle\":\"Example HR\","
                                + "\"siteSubtitle\":\"People operations\",\"footerHtml\":\"Footer\"}"))
                .andExpect(status().isOk());

        verify(siteSettingsService).save(any(SiteSettings.class));
    }
}
