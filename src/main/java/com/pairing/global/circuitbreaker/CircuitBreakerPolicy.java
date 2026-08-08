package com.pairing.global.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

/**
 * 도메인이 외부 API(AI 서버, 결제 PG 등) 호출용 서킷브레이커를 스스로 정의할 때 구현한다.
 * 구현체를 빈으로 등록하면 {@link GlobalCircuitBreakerRegistryConfig}가 자동으로 모아 등록한다.
 */
public interface CircuitBreakerPolicy {

    /** 서킷브레이커 고유 이름. {@code @CircuitBreaker(name = ...)} 에서 그대로 참조한다. */
    String getCircuitBreakerName();

    CircuitBreakerConfig getCircuitBreakerConfig();
}
