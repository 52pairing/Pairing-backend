package com.pairing.auth.infrastructure.redis;

import com.pairing.auth.application.port.EmailSendLimitPort;
import com.pairing.global.util.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 고정 윈도우 카운터.
 *
 * <p>첫 발송에서 TTL을 걸고 이후에는 INCR만 한다. 요구사항의 "15회를 넘기면 현재시간 +1시간 이후 재시도"가
 * 남은 TTL과 그대로 일치한다.
 */
@Component
@RequiredArgsConstructor
public class EmailSendLimitRedisAdapter implements EmailSendLimitPort {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public int increaseAndGet(String email, Duration window) {
        String key = key(email);
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }

        return count == null ? 0 : count.intValue();
    }

    @Override
    public Duration remainingWindow(String email) {
        Long seconds = redisTemplate.getExpire(key(email));
        return seconds == null || seconds < 0 ? Duration.ZERO : Duration.ofSeconds(seconds);
    }

    private String key(String email) {
        return RedisKeys.EMAIL_SEND_COUNT_PREFIX + email;
    }
}
