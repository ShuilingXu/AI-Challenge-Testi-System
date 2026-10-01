package com.autohr.modules.auth.config;

import com.autohr.modules.auth.entity.SysUser;
import com.autohr.modules.auth.mapper.SysUserMapper;
import com.autohr.modules.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final SysUserMapper sysUserMapper;
    private final AuthCookieService authCookieService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String token = authCookieService.read(request);
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        }
        if (token != null && !token.isBlank()) {
            try {
                authenticateToken(token);
            } catch (Exception ignored) {
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticateToken(String token) throws IOException, ServletException {
        Claims claims = jwtService.parseToken(token);
        String username = claims.getSubject();
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username).last("LIMIT 1"));
        if (user != null && Integer.valueOf(1).equals(user.getStatus())) {
            Long tokenUserId = claims.get("userId", Long.class);
            Integer tokenVersion = claims.get("tokenVersion", Integer.class);
            int currentVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
            if (tokenUserId == null || !tokenUserId.equals(user.getId())
                    || tokenVersion == null || tokenVersion != currentVersion) {
                return;
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    username,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRoleCode()))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }
}
