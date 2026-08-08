package com.pairing.global.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.time.Duration;

/**
 * Bucket4j가 Redis에 직접 붙는 전용 연결을 만든다. RedisTemplate과는 별개 커넥션이다
 * (Bucket4j-Redis 라이브러리가 Lettuce의 저수준 API를 요구해서 RedisTemplate으로 대체할 수 없다).
 *
 * <p>{@code @Lazy}: 이 빈은 {@code redisClient.connect(...)}로 즉시 연결을 맺는다.
 * global 패키지라 모든 컨텍스트 로딩에 걸리므로, 지연시키지 않으면 Redis가 없는 환경(CI 등)에서
 * 애플리케이션 전체가 기동조차 못 한다. 실제 레이트리밋을 쓰는 요청이 올 때만 연결한다.
 */
@Slf4j
@Configuration
public class RedisRateLimitConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:#{null}}")
    private String redisPassword;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean redisSslEnabled;

    @Bean
    @Lazy
    public ProxyManager<byte[]> lettuceProxyManager() {
        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .withSsl(redisSslEnabled);

        if (redisPassword != null && !redisPassword.isBlank()) {
            uriBuilder.withPassword(redisPassword.toCharArray());
        }

        RedisClient redisClient = RedisClient.create(uriBuilder.build());
        StatefulRedisConnection<byte[], byte[]> connection = redisClient.connect(ByteArrayCodec.INSTANCE);

        // 도메인별 최대 리밋 주기(1시간)보다 넉넉하게 TTL을 둬서, 다 쓴 버킷 키가 계속 안 남게 한다.
        ClientSideConfig clientSideConfig = ClientSideConfig.getDefault()
                .withExpirationAfterWriteStrategy(ExpirationAfterWriteStrategy.fixedTimeToLive(Duration.ofHours(2)));

        log.info("[Bucket4j] Redis Proxy Manager 연결 완료 (host={})", redisHost);
        return LettuceBasedProxyManager.builderFor(connection)
                .withClientSideConfig(clientSideConfig)
                .build();
    }
}
