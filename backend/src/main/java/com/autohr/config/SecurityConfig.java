package com.autohr.config;

import com.autohr.modules.auth.config.JwtAuthenticationFilter;
import com.autohr.modules.auth.config.CsrfCookieFilter;
import com.autohr.modules.auth.config.PasswordChangeRequiredFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; base-uri 'self'; object-src 'none'; "
            + "frame-ancestors 'none'; form-action 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: blob: https:; font-src 'self' data:; media-src 'self' data: blob: https:; "
            + "connect-src 'self' https: wss:; worker-src 'self' blob:; child-src 'self' blob:";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CsrfCookieFilter csrfCookieFilter;
    private final PasswordChangeRequiredFilter passwordChangeRequiredFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // API requests use the double-submit token enforced by CsrfCookieFilter.
        // Keep Spring Security's default CSRF protection enabled for non-API endpoints.
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/captcha").permitAll()
                        .requestMatchers("/api/exams/classes", "/api/exams/student-registration").permitAll()
                        .requestMatchers("/api/exams/admin/**").hasAnyAuthority("ROLE_IT_ADMIN", "ROLE_HR_ADMIN", "ROLE_HR_USER", "IT_ADMIN", "HR_ADMIN", "HR_USER")
                        .requestMatchers("/api/exams/student/**").hasAnyAuthority("ROLE_INTERVIEWEE", "INTERVIEWEE")
                        .requestMatchers("/api/site-settings/admin").hasAnyAuthority("ROLE_IT_ADMIN", "IT_ADMIN")
                        .requestMatchers("/api/system/config").hasAnyAuthority("ROLE_IT_ADMIN", "IT_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/site-settings").permitAll()
                        .requestMatchers("/api/interview/hr/knowledge-bases", "/api/interview/hr/knowledge-bases/**",
                                "/api/interview/hr/knowledge-items", "/api/interview/hr/knowledge-items/**",
                                "/api/interview/hr/process-templates", "/api/interview/hr/process-templates/**")
                        .hasAnyAuthority("ROLE_IT_ADMIN", "ROLE_HR_ADMIN", "ROLE_HR_USER", "IT_ADMIN", "HR_ADMIN", "HR_USER")
                        .requestMatchers(HttpMethod.GET, "/api/interview/interviewee/process/*",
                                "/api/interview/interviewee/next-question/*",
                                "/api/interview/interviewee/ai-records")
                        .hasAnyAuthority("ROLE_INTERVIEWEE", "INTERVIEWEE")
                        .requestMatchers(HttpMethod.POST, "/api/interview/interviewee/ai-answer")
                        .hasAnyAuthority("ROLE_INTERVIEWEE", "INTERVIEWEE")
                        .requestMatchers(HttpMethod.POST, "/api/interview/interviewee/anti-cheat-event")
                        .hasAnyAuthority("ROLE_INTERVIEWEE", "INTERVIEWEE")
                        .requestMatchers("/api/auth/me", "/api/auth/logout", "/api/auth/change-password").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(csrfCookieFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(passwordChangeRequiredFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
