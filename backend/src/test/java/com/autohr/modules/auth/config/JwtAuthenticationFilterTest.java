package com.autohr.modules.auth.config;

import com.autohr.modules.auth.entity.SysUser;
import com.autohr.modules.auth.mapper.SysUserMapper;
import com.autohr.modules.auth.service.impl.JwtServiceImpl;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    private static final String SECRET = "review-test-secret-with-more-than-32-characters";
    private final JwtServiceImpl jwt = new JwtServiceImpl(SECRET, 60_000);

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void oldTokenCannotAuthenticateRecreatedUsername() throws Exception {
        SysUser original = user(11L, 0);
        String token = jwt.generateToken(original);
        var mapper = mock(SysUserMapper.class);
        when(mapper.selectOne(any())).thenReturn(user(12L, 0));
        run(mapper, token);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void currentIdentityAuthenticatesButRevokedVersionDoesNot() throws Exception {
        var mapper = mock(SysUserMapper.class);
        var user = user(11L, 0);
        when(mapper.selectOne(any())).thenReturn(user);
        String token = jwt.generateToken(user);
        run(mapper, token);
        assertEquals("student_001", SecurityContextHolder.getContext().getAuthentication().getName());
        SecurityContextHolder.clearContext();
        user.setTokenVersion(1);
        run(mapper, token);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void signedTokensWithoutIdentityOrVersionAreRejected() throws Exception {
        var mapper = mock(SysUserMapper.class);
        when(mapper.selectOne(any())).thenReturn(user(11L, 0));
        for (String missing : new String[]{"userId", "tokenVersion"}) {
            var builder = Jwts.builder().subject("student_001").expiration(new Date(System.currentTimeMillis() + 60_000));
            if (!missing.equals("userId")) builder.claim("userId", 11L);
            if (!missing.equals("tokenVersion")) builder.claim("tokenVersion", 0);
            run(mapper, builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }
    }

    private void run(SysUserMapper mapper, String token) throws Exception {
        var filter = new JwtAuthenticationFilter(jwt, mapper, new AuthCookieService("AUTOHR_SESSION", false, "Lax", 60_000));
        var request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
    }

    private SysUser user(Long id, int version) {
        var user = new SysUser();
        user.setId(id); user.setUsername("student_001"); user.setStatus(1);
        user.setRoleCode("STUDENT"); user.setTokenVersion(version);
        return user;
    }
}
