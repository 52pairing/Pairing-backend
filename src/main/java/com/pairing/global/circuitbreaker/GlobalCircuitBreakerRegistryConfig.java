package com.pairing.global.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** 각 도메인이 정의한 {@link CircuitBreakerPolicy} 빈을 전부 모아 레지스트리에 등록한다. */
@Configuration
@RequiredArgsConstructor
public class GlobalCircuitBreakerRegistryConfig {

    private final CircuitBreakerRegistry registry;
    private final List<CircuitBreakerPolicy> policies;

    @PostConstruct
    public void registerDomainPolicies() {
        for (CircuitBreakerPolicy policy : policies) {
            registry.circuitBreaker(policy.getCircuitBreakerName(), policy.getCircuitBreakerConfig());
        }
    }
}
