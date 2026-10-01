package com.autohr.modules.auth.config;

import com.autohr.config.SecurityConfig;
import com.autohr.config.SpaController;
import com.autohr.modules.auth.entity.SysUser;
import com.autohr.modules.auth.mapper.SysUserMapper;
import com.autohr.modules.auth.service.impl.JwtServiceImpl;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SpaController.class)
@Import({SecurityConfig.class, JwtServiceImpl.class, JwtAuthenticationFilter.class,
        AuthCookieService.class, CsrfCookieFilter.class, PasswordChangeRequiredFilter.class})
@TestPropertySource(properties = {"jwt.secret=page-security-test-secret-over-32-characters", "jwt.expiration=60000"})
class PasswordChangePageSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JwtServiceImpl jwt;
    @MockBean SysUserMapper mapper;
    private Cookie session;

    @BeforeEach
    void signedInitialPasswordSession() {
        var user = new SysUser();
        user.setId(42L); user.setUsername("teacher"); user.setStatus(1);
        user.setRoleCode("LECTURER"); user.setMustChangePassword(1); user.setTokenVersion(0);
        when(mapper.selectOne(any())).thenReturn(user);
        session = new Cookie("AUTOHR_SESSION", jwt.generateToken(user));
    }

    @Test
    void initialPasswordSessionCanReloadPagesAndAssets() throws Exception {
        for (String path : new String[]{"/change-password", "/changepasswd", "/login"}) {
            mvc.perform(get(path).cookie(session)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
        mvc.perform(get("/index.html").cookie(session)).andExpect(status().isOk());
        mvc.perform(get("/assets/review.js").cookie(session)).andExpect(status().isOk());
    }

    @Test
    void initialPasswordSessionStillCannotAccessBusinessApis() throws Exception {
        mvc.perform(get("/api/exams/admin/exams").cookie(session))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }
}
