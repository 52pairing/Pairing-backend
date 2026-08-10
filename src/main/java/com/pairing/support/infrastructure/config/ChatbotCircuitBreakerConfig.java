package com.pairing.support.infrastructure.config;

import com.pairing.global.circuitbreaker.CircuitBreakerPolicy;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;

/**
 * Pairing-python(AI 서버) 챗봇 호출 서킷브레이커. matching 도메인과 같은 값을 쓴다.
 *
 * <p>이 빈이 없으면 {@code @CircuitBreaker(name = "pythonChatbotApi")} 가 resilience4j 기본값으로
 * 동작한다. 기본값은 {@code minimumNumberOfCalls} 가 100이라, 계정당 하루 10회로 제한된 챗봇에서는
 * 서킷이 사실상 열리지 않는다.
 */
@Component
public class ChatbotCircuitBreakerConfig implements CircuitBreakerPolicy {

    @Override
    public String getCircuitBreakerName() {
        return "pythonChatbotApi";
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
                // 4xx는 AI 서버 장애가 아니라 우리 요청이 잘못된 것이라 실패율에 넣지 않는다.
                .ignoreExceptions(HttpClientErrorException.class)
                .build();
    }
}
