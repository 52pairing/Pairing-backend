package com.pairing.auth.infrastructure.redis;

import com.pairing.auth.application.port.SessionRegistryPort;
import com.pairing.global.util.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class SessionRegistryRedisAdapter implements SessionRegistryPort {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void register(Long accountId, String sessionId, Duration ttl) {
        redisTemplate.opsForValue().set(key(accountId), sessionId, ttl);
    }

    @Override
    public boolean isAlive(Long accountId, String sessionId) {
        String current = redisTemplate.opsForValue().get(key(accountId));
        // 세션 정보가 없으면(로그아웃/만료) 살아 있다고 보지 않는다.
        return current != null && current.equals(sessionId);
    }

    @Override
    public void clear(Long accountId) {
        redisTemplate.delete(key(accountId));
    }

    private String key(Long accountId) {
        return RedisKeys.SESSION_PREFIX + accountId;
    }
}
