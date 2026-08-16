package com.autohr.modules.auth.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

@Component
public class AuthCookieService {

    private static final Set<String> SAME_SITE_VALUES = Set.of("Lax", "Strict", "None");

    private final String cookieName;
    private final boolean secure;
    private final String sameSite;
    private final Duration maxAge;

    public AuthCookieService(
            @Value("${auth.session-cookie.name:AUTOHR_SESSION}") String cookieName,
            @Value("${auth.session-cookie.secure:true}") boolean secure,
            @Value("${auth.session-cookie.same-site:Lax}") String sameSite,
            @Value("${auth.session-cookie.max-age-ms:${jwt.expiration:7200000}}") long maxAgeMs) {
        this.cookieName = cookieName;
        this.secure = secure;
        this.sameSite = SAME_SITE_VALUES.contains(sameSite) ? sameSite : "Lax";
        this.maxAge = Duration.ofMillis(Math.max(60_000L, maxAgeMs));
    }

    public void write(HttpServletResponse response, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, build(token, maxAge, true).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO, true).toString());
    }

    public String read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie build(String value, Duration age, boolean httpOnly) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(httpOnly)
                .secure(secure)
                .path("/")
                .sameSite(sameSite)
                .maxAge(age)
                .build();
    }
}
