package com.autohr.modules.auth.service.impl;

import com.autohr.modules.auth.entity.SysUser;
import com.autohr.modules.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

@Service
public class JwtServiceImpl implements JwtService {

    private static final Set<String> INSECURE_SECRETS = Set.of(
            "AUTOHR_DEMO_JWT_SECRET_2026_LOCAL_ONLY_1234567890",
            "AUTOHR_SYSTEM_SECRET_KEY_2026_SECURE_TOKEN_1234567890"
    );

    private final SecretKey secretKey;
    private final long expiration;

    public JwtServiceImpl(@Value("${jwt.secret}") String secret,
                           @Value("${jwt.expiration}") long expiration) {
        if (secret == null || secret.length() < 32 || INSECURE_SECRETS.contains(secret)) {
            throw new IllegalStateException("jwt.secret must be configured with a non-default secret of at least 32 characters");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    @Override
    public String generateToken(SysUser user) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("roleCode", user.getRoleCode())
                .claim("userId", user.getId())
                .claim("tokenVersion", user.getTokenVersion() == null ? 0 : user.getTokenVersion())
                .issuedAt(now)
                .expiration(expireAt)
                .signWith(secretKey)
                .compact();
    }

    @Override
    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }

    @Override
    public String extractUsername(String token) {
        return parseToken(token).getSubject();
    }
}
