package com.pairing.auth.infrastructure.redis;

import com.pairing.auth.application.port.TokenStorePort;
import com.pairing.global.util.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TokenStoreRedisAdapter implements TokenStorePort {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void save(Long accountId, String refreshToken, Duration ttl) {
        redisTemplate.opsForValue().set(key(accountId), refreshToken, ttl);
    }

    @Override
    public Optional<String> find(Long accountId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(accountId)));
    }

    @Override
    public void delete(Long accountId) {
        redisTemplate.delete(key(accountId));
    }

    private String key(Long accountId) {
        return RedisKeys.REFRESH_TOKEN_PREFIX + accountId;
    }
}
