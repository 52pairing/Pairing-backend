package com.pairing.auth.infrastructure.redis;

import com.pairing.auth.application.port.LoginAttemptPort;
import com.pairing.global.util.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** IP 기준 실패 카운트와 차단. (1시간 20회 -> 2시간 차단) */
@Component
@RequiredArgsConstructor
public class LoginAttemptRedisAdapter implements LoginAttemptPort {

    private static final String BLOCK_VALUE = "1";

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean isBlocked(String ip) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(blockKey(ip)));
    }

    @Override
    public Duration blockRemaining(String ip) {
        Long seconds = redisTemplate.getExpire(blockKey(ip));
        return seconds == null || seconds < 0 ? Duration.ZERO : Duration.ofSeconds(seconds);
    }

    @Override
    public void recordFailure(String ip, int maxFailure, Duration window, Duration blockDuration) {
        String key = failKey(ip);
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }

        if (count != null && count >= maxFailure) {
            redisTemplate.opsForValue().set(blockKey(ip), BLOCK_VALUE, blockDuration);
            // 차단으로 넘어갔으면 카운터는 역할을 다했다. 남겨두면 해제 직후 바로 재차단된다.
            redisTemplate.delete(key);
        }
    }

    @Override
    public void clearFailure(String ip) {
        redisTemplate.delete(failKey(ip));
    }

    private String failKey(String ip) {
        return RedisKeys.LOGIN_FAIL_IP_PREFIX + ip;
    }

    private String blockKey(String ip) {
        return RedisKeys.LOGIN_BLOCK_IP_PREFIX + ip;
    }
}
