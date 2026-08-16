package com.autohr.modules.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

@Component
public class CsrfCookieFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String cookieName;
    private final String headerName;
    private final boolean secure;
    private final String sameSite;

    public CsrfCookieFilter(
            @Value("${auth.csrf-cookie.name:AUTOHR_CSRF}") String cookieName,
            @Value("${auth.csrf-cookie.header:X-CSRF-Token}") String headerName,
            @Value("${auth.csrf-cookie.secure:${auth.session-cookie.secure:true}}") boolean secure,
            @Value("${auth.csrf-cookie.same-site:${auth.session-cookie.same-site:Lax}}") String sameSite) {
        this.cookieName = cookieName;
        this.headerName = headerName;
        this.secure = secure;
        this.sameSite = Set.of("Lax", "Strict", "None").contains(sameSite) ? sameSite : "Lax";
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = readCookie(request);
        if (token == null || token.isBlank()) {
            token = createToken();
            response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(cookieName, token)
                    .httpOnly(false)
                    .secure(secure)
                    .sameSite(sameSite)
                    .path("/")
                    .maxAge(Duration.ofHours(12))
                    .build().toString());
        }

        if (isApiRequest(request) && !SAFE_METHODS.contains(request.getMethod())
                && !matches(token, request.getHeader(headerName))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "CSRF token is missing or invalid");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    private String readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private String createToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean matches(String expected, String actual) {
        if (actual == null || actual.isBlank()) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
