package com.pairing.matching.infrastructure.llm;

import com.pairing.global.exception.BusinessException;
import com.pairing.matching.application.result.MatchingRecommendation;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * AI 서버(Pairing-python) 호출의 재시도 정책이 <b>실제로 그대로 도는지</b> 확인한다.
 *
 * <p>애노테이션과 yaml 설정의 조합은 조용히 안 먹는 일이 흔하다. 실제로 이 어댑터의 차단기는 설정이
 * 없어 기본값({@code minimum-number-of-calls=100})으로 돌고 있었고, 이 호출량에서는 영원히 열리지
 * 않았다 — 붙어만 있고 동작은 안 한 상태였다(2026-08-13).
 *
 * <p><b>fallback 위치가 중요하다.</b> 예전엔 {@code @CircuitBreaker}에 fallback이 달려 있어 원래 예외가
 * {@code BusinessException}으로 바뀐 뒤 재시도 조건과 대조됐다. 그러면 {@code retry-exceptions}에
 * 무엇을 적든 <b>재시도가 한 번도 안 걸린다</b>. 그래서 fallback을 {@code @Retry} 쪽으로 옮겼다.
 */
// 실제 빈과 **같은 이름**으로 덮어써야 한다. 어댑터가 @Qualifier 로 이름을 못박고 있고, 그 빈은
// defaultCandidate = false 라 이름을 안 맞추면 목이 아니라 진짜 클라이언트가 주입된다.
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@Import(PythonMatchingAdapterRetryTest.MockPythonServerConfig.class)
class PythonMatchingAdapterRetryTest {

    private static final String RECOMMEND_URL = "http://ai.test/api/v1/matchings/recommendations";

    /** {@link MockRestServiceServer}는 빌더에 팩토리를 심는 방식이라 빈을 만들 때 같이 잡아야 한다. */
    private static MockRestServiceServer mockServer;

    @TestConfiguration
    static class MockPythonServerConfig {

        @Bean
        RestClient pythonMatchingRestClient() {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
            mockServer = MockRestServiceServer.bindTo(builder).build();
            return builder.build();
        }
    }

    @Autowired
    private PythonMatchingAdapter adapter;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetServerAndCircuitBreaker() {
        mockServer.reset();
        // 차단기는 빈이라 테스트끼리 실패 기록이 쌓인다. 안 지우면 앞 테스트가 남긴 실패 때문에
        // 뒤 테스트에서 차단기가 열려 재시도가 도중에 끊긴다(실제로 3회가 2회로 줄었다).
        circuitBreakerRegistry.circuitBreaker("pythonMatchingApi").reset();
    }

    @Test
    @DisplayName("차단기 설정이 실제로 붙는다 — 기본값(100회)이면 이 호출량에서 영원히 안 열린다")
    void circuitBreakerConfigIsActuallyApplied() {
        // 애노테이션만 붙어 있고 설정이 없으면 resilience4j 기본값(minimumNumberOfCalls=100,
        // slidingWindowSize=100)으로 돈다. 매칭 호출은 착수금 결제 시 한 번씩이라 100번이 쌓일 일이
        // 없어 **차단기가 절대 안 열린다**. 실제로 그 상태로 배포돼 있었다(2026-08-13).
        //
        // 챗봇(support)은 CircuitBreakerPolicy 빈으로 같은 문제를 해결했다. 매칭은 yaml 로 했는데,
        // 둘 다 같은 레지스트리를 쓰고 이름이 달라(pythonChatbotApi vs pythonMatchingApi) 충돌하지 않는다.
        var config = circuitBreakerRegistry.circuitBreaker("pythonMatchingApi").getCircuitBreakerConfig();

        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50.0f);
    }

    @Test
    @DisplayName("연결이 안 되면 다시 부른다 — 두 번째에 성공하면 그대로 결과를 돌려준다")
    void retriesWhenTheConnectionFailsAndSucceedsOnSecondAttempt() {
        // 파이썬 재배포·재시작 중에 나는 실패가 이것이다. Gemini 를 부르기 전이라 재시도해도
        // 추가 비용이 없고, 다시 부르면 성공한다.
        mockServer.expect(times(1), requestTo(RECOMMEND_URL))
                .andRespond(withException(new IOException("Connection refused")));
        mockServer.expect(times(1), requestTo(RECOMMEND_URL))
                .andRespond(withSuccess("""
                        {"code":"AI_000","message":"ok","data":{
                          "position_id":33,"model":"gemini-2.0-flash",
                          "candidates":[{"freelancer_id":7,"score":91.0,"reason":"스킬 일치","similarity":0.81}]}}
                        """, MediaType.APPLICATION_JSON));

        MatchingRecommendation result = adapter.recommend(33L, 2, 3, List.of(), 9_000_000L);

        assertThat(result.candidates()).hasSize(1);
        mockServer.verify();
    }

    @Test
    @DisplayName("계속 실패하면 3번까지만 부르고 MT_010 으로 끝낸다 — 무한히 매달리지 않는다")
    void stopsAfterMaxAttempts() {
        mockServer.expect(times(3), requestTo(RECOMMEND_URL))
                .andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> adapter.recommend(33L, 2, 3, List.of(), 9_000_000L))
                .isInstanceOf(BusinessException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("502(LLM 호출 실패)는 재시도하지 않는다 — 파이썬이 이미 Gemini 를 재시도한 뒤다")
    void doesNotRetryWhenPythonAlreadyExhaustedItsOwnGeminiRetries() {
        // 파이썬은 Gemini 실패를 502(AI_012/AI_014), 504(AI_013)로 내려준다. 그 시점엔 파이썬이
        // 같은 키로 2회 + 키 회전까지 이미 마친 뒤다. 여기서 또 부르면 한 번의 추천에 Gemini
        // 호출이 9회 이상으로 불어나는데, 성공 확률은 거의 안 오르고 비용만 는다.
        //
        // 기대를 1회만 걸어둔다. 재시도가 걸리면 두 번째 요청이 예상 밖이라 verify 에서 걸린다.
        mockServer.expect(times(1), requestTo(RECOMMEND_URL))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"AI_012","message":"LLM 호출에 실패했습니다.","data":null}"""));

        assertThatThrownBy(() -> adapter.recommend(33L, 2, 3, List.of(), 9_000_000L))
                .isInstanceOf(BusinessException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("후보 없음(404 AI_020)은 재시도하지 않고 빈 결과로 돌려준다 — 장애가 아니다")
    void doesNotRetryWhenCandidatePoolIsEmpty() {
        mockServer.expect(times(1), requestTo(RECOMMEND_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"AI_020","message":"추천할 후보가 없습니다.","data":null}"""));

        MatchingRecommendation result = adapter.recommend(33L, 2, 3, List.of(), 9_000_000L);

        assertThat(result.candidates()).isEmpty();
        mockServer.verify();
    }
}
