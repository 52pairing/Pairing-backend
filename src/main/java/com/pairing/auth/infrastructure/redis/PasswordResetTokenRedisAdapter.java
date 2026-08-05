package com.pairing.auth.infrastructure.redis;

import com.pairing.auth.application.port.PasswordResetTokenPort;
import com.pairing.global.util.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PasswordResetTokenRedisAdapter implements PasswordResetTokenPort {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void save(String token, Long accountId, Duration ttl) {
        redisTemplate.opsForValue().set(key(token), String.valueOf(accountId), ttl);
    }

    @Override
    public Optional<Long> findAccountId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(token))).map(Long::valueOf);
    }

    @Override
    public void delete(String token) {
        redisTemplate.delete(key(token));
    }

    private String key(String token) {
        return RedisKeys.PASSWORD_RESET_PREFIX + token;
    }
}
