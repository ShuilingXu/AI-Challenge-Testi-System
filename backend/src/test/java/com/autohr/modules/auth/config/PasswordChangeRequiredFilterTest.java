package com.autohr.modules.auth.config;

import com.autohr.modules.auth.entity.SysUser;
import com.autohr.modules.auth.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class PasswordChangeRequiredFilterTest {
    @Test
    void allowsSessionRecoveryButBlocksExamUntilPasswordChanges() throws Exception {
        var mapper = mock(SysUserMapper.class);
        var user = new SysUser();
        user.setMustChangePassword(1);
        when(mapper.selectOne(any())).thenReturn(user);
        var filter = new PasswordChangeRequiredFilter(mapper);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student", null, List.of()));
        try {
            for (String path : List.of("/api/auth/me", "/api/auth/login", "/api/auth/captcha", "/api/auth/change-password", "/api/auth/logout")) {
                var response = new MockHttpServletResponse();
                filter.doFilter(new MockHttpServletRequest("GET", path), response,
                        (req, res) -> res.getWriter().write("allowed"));
                assertEquals("allowed", response.getContentAsString());
            }
            var response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("GET", "/api/exams/student/exams"), response,
                    (req, res) -> fail("must not reach protected endpoint"));
            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains("PASSWORD_CHANGE_REQUIRED"));
            user.setMustChangePassword(0);
            response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("GET", "/api/exams/student/exams"), response,
                    (req, res) -> res.getWriter().write("allowed"));
            assertEquals("allowed", response.getContentAsString());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
