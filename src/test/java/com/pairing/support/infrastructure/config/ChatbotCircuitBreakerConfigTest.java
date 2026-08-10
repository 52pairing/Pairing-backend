package com.pairing.support.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서킷브레이커 정책 빈이 레지스트리에 실제로 등록되는지 확인한다.
 *
 * <p>{@code @CircuitBreaker(name = ...)} 는 이름이 등록돼 있지 않아도 기본값으로 조용히 동작해서,
 * 정책 빈을 빠뜨려도 아무 데서도 티가 나지 않는다. 그래서 등록 여부를 테스트로 고정한다.
 */
@SpringBootTest
class ChatbotCircuitBreakerConfigTest {

    @Autowired
    private CircuitBreakerRegistry registry;

    @Test
    @DisplayName("챗봇 서킷브레이커가 기본값이 아니라 도메인이 정한 값으로 등록된다")
    void chatbotCircuitBreakerUsesDomainPolicy() {
        CircuitBreakerConfig config = registry.circuitBreaker("pythonChatbotApi").getCircuitBreakerConfig();
        CircuitBreakerConfig defaults = CircuitBreakerConfig.ofDefaults();

        // 기본값(100)이면 하루 10회 제한인 챗봇에서는 서킷이 사실상 열리지 않는다.
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5)
                .isNotEqualTo(defaults.getMinimumNumberOfCalls());
        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50);
    }
}
