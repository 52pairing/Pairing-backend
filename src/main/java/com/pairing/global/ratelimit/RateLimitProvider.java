package com.pairing.global.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Redis에 분산 저장되는 버킷을 도메인별·식별자별로 가져온다(여러 서버 인스턴스에서도 같은 한도를 공유). */
@Component
@RequiredArgsConstructor
public class RateLimitProvider {

    private final ProxyManager<byte[]> proxyManager;

    public Bucket getBucket(RateLimitPolicy policy, String identifier) {
        String key = "rate_limit:" + policy.getDomainPrefix() + ":" + identifier;
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        return proxyManager.builder().build(keyBytes, policy::getBucketConfiguration);
    }
}
