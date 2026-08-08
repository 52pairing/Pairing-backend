package com.pairing.matching.infrastructure.config;

import com.pairing.global.ratelimit.RateLimitPolicy;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 재추천 요청(Pairing-python 재호출) 레이트리밋.
 *
 * <p>무료/유료 횟수 자체는 비즈니스 로직(freeRerecommendAvailable/paidRerecommendRemaining)이
 * 이미 막지만, 그 카운터가 갱신되기 전에 짧은 시간에 여러 번 눌러 AI 서버를 반복 호출하는 것을
 * 막기 위한 방어선이다.
 */
@Component
public class MatchingRateLimitConfig implements RateLimitPolicy {

    @Override
    public String getDomainPrefix() {
        return "matching-rerecommend";
    }

    @Override
    public BucketConfiguration getBucketConfiguration() {
        Bandwidth burstLimit = Bandwidth.classic(1, Refill.intervally(1, Duration.ofSeconds(5)));
        Bandwidth sustainedLimit = Bandwidth.classic(10, Refill.intervally(10, Duration.ofHours(1)));
        return BucketConfiguration.builder().addLimit(burstLimit).addLimit(sustainedLimit).build();
    }
}
