package com.pairing.global.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Redis에 분산 저장되는 버킷을 도메인별·식별자별로 가져온다(여러 서버 인스턴스에서도 같은 한도를 공유). */
@Component
public class RateLimitProvider {

    private final ProxyManager<byte[]> proxyManager;

    /**
     * {@code @Lazy}: lettuceProxyManager는 실제 Redis 연결을 즉시 맺는 빈이다.
     * 생성자 주입 지점에도 명시해야 지연 프록시가 적용된다(빈 쪽 @Lazy만으로는
     * 즉시 생성되는 다른 싱글톤의 생성자 주입까지 미뤄지지 않는다).
     */
    public RateLimitProvider(@Lazy ProxyManager<byte[]> proxyManager) {
        this.proxyManager = proxyManager;
    }

    public Bucket getBucket(RateLimitPolicy policy, String identifier) {
        String key = "rate_limit:" + policy.getDomainPrefix() + ":" + identifier;
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        return proxyManager.builder().build(keyBytes, policy::getBucketConfiguration);
    }
}
