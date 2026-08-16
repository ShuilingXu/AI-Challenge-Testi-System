package com.autohr.modules.auth.config;

import com.autohr.modules.auth.entity.SysUser;
import com.autohr.modules.auth.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/auth/me", "/api/auth/change-password", "/api/auth/logout"
    );

    private final SysUserMapper sysUserMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod()) || ALLOWED_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, authentication.getName()).last("LIMIT 1"));
            if (user != null && Integer.valueOf(1).equals(user.getMustChangePassword())) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"必须先修改初始密码\",\"data\":null}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
