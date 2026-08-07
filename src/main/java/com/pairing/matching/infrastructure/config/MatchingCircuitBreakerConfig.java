package com.pairing.matching.infrastructure.config;

import com.pairing.global.circuitbreaker.CircuitBreakerPolicy;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;

/** Pairing-python(AI 서버) 호출 서킷브레이커. Algoga_V3_backend의 chatbot 설정과 동일한 값을 쓴다. */
@Component
public class MatchingCircuitBreakerConfig implements CircuitBreakerPolicy {

    @Override
    public String getCircuitBreakerName() {
        return "pythonMatchingApi";
    }

    @Override
    public CircuitBreakerConfig getCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(10))
                .slowCallRateThreshold(40)
                .waitDurationInOpenState(Duration.ofSeconds(20))
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .permittedNumberOfCallsInHalfOpenState(2)
                .ignoreExceptions(HttpClientErrorException.class)
                .build();
    }
}
