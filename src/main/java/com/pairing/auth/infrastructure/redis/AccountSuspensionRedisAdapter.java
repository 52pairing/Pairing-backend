package com.pairing.auth.infrastructure.redis;

import com.pairing.auth.application.port.AccountSuspensionPort;
import com.pairing.global.util.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountSuspensionRedisAdapter implements AccountSuspensionPort {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean isSuspended(Long accountId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.SUSPEND_PREFIX + accountId));
    }
}
