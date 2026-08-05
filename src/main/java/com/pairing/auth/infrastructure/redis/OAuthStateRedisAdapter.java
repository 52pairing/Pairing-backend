package com.pairing.auth.infrastructure.redis;

import com.pairing.auth.application.port.OAuthStatePort;
import com.pairing.global.util.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class OAuthStateRedisAdapter implements OAuthStatePort {

    private static final String EMPTY_RETURN_URL = "";

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void save(String state, String returnUrl, Duration ttl) {
        redisTemplate.opsForValue().set(key(state), returnUrl == null ? EMPTY_RETURN_URL : returnUrl, ttl);
    }

    @Override
    public boolean consume(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        // 존재 여부 확인과 삭제를 한 번에 처리해야 같은 state 를 두 번 쓸 수 없다.
        return Boolean.TRUE.equals(redisTemplate.delete(key(state)));
    }

    private String key(String state) {
        return RedisKeys.OAUTH_STATE_PREFIX + state;
    }
}
