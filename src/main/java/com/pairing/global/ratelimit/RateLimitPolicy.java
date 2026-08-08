package com.pairing.global.ratelimit;

import io.github.bucket4j.BucketConfiguration;

/** 도메인이 자기 엔드포인트의 레이트리밋 규칙을 정의할 때 구현한다(Redis 분산 버킷 기준). */
public interface RateLimitPolicy {

    /** Redis 키 접두사(도메인 식별자). */
    String getDomainPrefix();

    BucketConfiguration getBucketConfiguration();
}
