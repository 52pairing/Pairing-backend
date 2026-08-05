package com.pairing.auth.infrastructure.redis;

import com.pairing.auth.application.port.VerifiedMarkerPort;
import com.pairing.auth.domain.model.VerificationPurpose;
import com.pairing.global.util.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class VerifiedMarkerRedisAdapter implements VerifiedMarkerPort {

    private static final String MARKER_VALUE = "1";

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void mark(String email, VerificationPurpose purpose, Duration ttl) {
        redisTemplate.opsForValue().set(key(email, purpose), MARKER_VALUE, ttl);
    }

    @Override
    public boolean isVerified(String email, VerificationPurpose purpose) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(email, purpose)));
    }

    @Override
    public void clear(String email, VerificationPurpose purpose) {
        redisTemplate.delete(key(email, purpose));
    }

    private String key(String email, VerificationPurpose purpose) {
        return RedisKeys.AUTH_SUCCESS_PREFIX + purpose.name() + ":" + email;
    }
}
