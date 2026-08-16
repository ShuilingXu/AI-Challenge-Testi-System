package com.autohr.modules.auth.service;

import com.autohr.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@Component
public class AuthRedisSecurityStore {

    private static final DefaultRedisScript<Long> CONSUME_CAPTCHA_SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); "
                    + "if not value then return 0 end; "
                    + "redis.call('DEL', KEYS[1]); "
                    + "if value == ARGV[1] then return 1 end; "
                    + "return -1",
            Long.class
    );
    private static final DefaultRedisScript<Long> STORE_VERIFICATION_CODE_SCRIPT = new DefaultRedisScript<>(
            "local sentAt = redis.call('HGET', KEYS[1], 'sentAt'); "
                    + "if sentAt and tonumber(sentAt) + tonumber(ARGV[1]) > tonumber(ARGV[2]) then return 0 end; "
                    + "redis.call('HSET', KEYS[1], 'code', ARGV[3], 'sentAt', ARGV[2], 'attempts', '0'); "
                    + "redis.call('EXPIRE', KEYS[1], ARGV[4]); "
                    + "return 1",
            Long.class
    );
    private static final DefaultRedisScript<Long> CONSUME_VERIFICATION_CODE_SCRIPT = new DefaultRedisScript<>(
            "local expected = redis.call('HGET', KEYS[1], 'code'); "
                    + "if not expected then return 0 end; "
                    + "if expected == ARGV[1] then redis.call('DEL', KEYS[1]); return 1 end; "
                    + "local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1); "
                    + "if tonumber(attempts) >= tonumber(ARGV[2]) then redis.call('DEL', KEYS[1]); return -2 end; "
                    + "return -1",
            Long.class
    );
    private static final DefaultRedisScript<Long> DELETE_VERIFICATION_CODE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('HGET', KEYS[1], 'code') == ARGV[1] then redis.call('DEL', KEYS[1]); return 1 end; "
                    + "return 0",
            Long.class
    );
    private static final DefaultRedisScript<Long> INCREMENT_RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end; "
                    + "return count",
            Long.class
    );
    private static final DefaultRedisScript<Long> CLAIM_RATE_LIMITED_EVENT_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end; "
                    + "local count = redis.call('INCR', KEYS[2]); "
                    + "if count == 1 then redis.call('EXPIRE', KEYS[2], ARGV[1]) end; "
                    + "if count > tonumber(ARGV[2]) then return -1 end; "
                    + "redis.call('SET', KEYS[1], '1', 'EX', ARGV[3]); "
                    + "return 1",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public AuthRedisSecurityStore(StringRedisTemplate redisTemplate,
                                  @Value("${auth.security.redis-key-prefix:autohr:auth}") String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? "autohr:auth" : keyPrefix.trim();
    }

    public void storeCaptcha(String captchaId, String code, Duration ttl) {
        execute(() -> {
            redisTemplate.opsForValue().set(captchaKey(captchaId), code, ttl);
            return null;
        });
    }

    public CaptchaResult consumeCaptcha(String captchaId, String code) {
        Long result = execute(() -> redisTemplate.execute(CONSUME_CAPTCHA_SCRIPT, List.of(captchaKey(captchaId)), code));
        if (result == null) {
            throw unavailable();
        }
        if (result == 1L) {
            return CaptchaResult.MATCHED;
        }
        if (result == 0L) {
            return CaptchaResult.MISSING;
        }
        if (result == -1L) {
            return CaptchaResult.MISMATCHED;
        }
        throw unavailable();
    }

    public boolean replaceVerificationCode(String purpose, String target, String code, long nowEpochSecond,
                                           int resendSeconds, int expireSeconds) {
        Long result = execute(() -> redisTemplate.execute(STORE_VERIFICATION_CODE_SCRIPT,
                List.of(verificationKey(purpose, target)),
                String.valueOf(resendSeconds), String.valueOf(nowEpochSecond), code, String.valueOf(expireSeconds)));
        if (result == null) {
            throw unavailable();
        }
        return result == 1L;
    }

    public void discardVerificationCode(String purpose, String target, String code) {
        execute(() -> redisTemplate.execute(DELETE_VERIFICATION_CODE_SCRIPT,
                List.of(verificationKey(purpose, target)), code));
    }

    public VerificationResult consumeVerificationCode(String purpose, String target, String code, int maxAttempts) {
        Long result = execute(() -> redisTemplate.execute(CONSUME_VERIFICATION_CODE_SCRIPT,
                List.of(verificationKey(purpose, target)), code, String.valueOf(maxAttempts)));
        if (result == null) {
            throw unavailable();
        }
        if (result == 1L) {
            return VerificationResult.MATCHED;
        }
        if (result == 0L) {
            return VerificationResult.MISSING;
        }
        if (result == -1L) {
            return VerificationResult.MISMATCHED;
        }
        if (result == -2L) {
            return VerificationResult.LOCKED;
        }
        throw unavailable();
    }

    public void enforceRateLimit(String scope, String identity, int limit, int windowSeconds, String message) {
        if (limit < 1 || windowSeconds < 1) {
            throw new IllegalStateException("Authentication rate-limit configuration must be positive");
        }
        Long count = execute(() -> redisTemplate.execute(INCREMENT_RATE_LIMIT_SCRIPT,
                List.of(rateLimitKey(scope, identity)), String.valueOf(windowSeconds)));
        if (count == null) {
            throw unavailable();
        }
        if (count > limit) {
            throw new BusinessException(message);
        }
    }

    public boolean claimRateLimitedEvent(String scope, String identity, String eventId, int limit,
                                         int windowSeconds, int eventTtlSeconds, String message) {
        if (limit < 1 || windowSeconds < 1 || eventTtlSeconds < 1) {
            throw new IllegalStateException("Event rate-limit configuration must be positive");
        }
        Long result = execute(() -> redisTemplate.execute(CLAIM_RATE_LIMITED_EVENT_SCRIPT,
                List.of(idempotencyKey(scope, identity, eventId), rateLimitKey(scope, identity)),
                String.valueOf(windowSeconds), String.valueOf(limit), String.valueOf(eventTtlSeconds)));
        if (result == null) {
            throw unavailable();
        }
        if (result == -1L) {
            throw new BusinessException(message);
        }
        return result == 1L;
    }

    private String captchaKey(String captchaId) {
        return keyPrefix + ":captcha:" + captchaId;
    }

    private String verificationKey(String purpose, String target) {
        return keyPrefix + ":verification:" + fingerprint(purpose + ':' + target);
    }

    private String rateLimitKey(String scope, String identity) {
        return keyPrefix + ":rate:" + scope + ':' + fingerprint(identity);
    }

    private String idempotencyKey(String scope, String identity, String eventId) {
        return keyPrefix + ":event:" + scope + ':' + fingerprint(identity + ':' + eventId);
    }

    private String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private <T> T execute(RedisOperation<T> operation) {
        try {
            return operation.execute();
        } catch (RuntimeException ex) {
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException("认证安全服务暂不可用，请稍后重试");
    }

    @FunctionalInterface
    private interface RedisOperation<T> {
        T execute();
    }

    public enum CaptchaResult {
        MATCHED,
        MISSING,
        MISMATCHED
    }

    public enum VerificationResult {
        MATCHED,
        MISSING,
        MISMATCHED,
        LOCKED
    }
}
