package com.pairing.contract.infrastructure.config;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;

/**
 * 계약서 문구 AI 호출의 재시도 정책. {@code ContractDraftAdapter} 만 쓴다.
 *
 * <p><b>yaml 이 아니라 자바 빈으로 둔다.</b> {@code matching-retry.yaml} 에 얹으면 그 파일이
 * "매칭 어댑터만 쓴다 · 이 파일이 값의 유일한 출처"라고 못박은 전제가 깨진다. 새 yaml 을
 * 만들면 운영·테스트 application.yaml 양쪽에 import 를 넣어야 하는데, 한쪽만 고쳐져서
 * 테스트가 실제와 다른 설정을 검증하는 사고가 매칭 쪽에서 이미 날 뻔했다.
 * 차단기는 이미 도메인별 자바 빈으로 등록하고 있으므로 재시도만 yaml 로 둘 이유가 없다.
 *
 * <p><b>즉시 재시도는 연결 실패만 잡는다.</b> 파이썬이 5xx(AI_012/AI_013/AI_014)를 내려줄 땐
 * 이미 Gemini 를 같은 키로 재시도하고 키까지 돌려 본 뒤다. 게다가 한도에 걸린 키는 300초
 * 쿨다운에 들어가 있어 1초 뒤 재호출은 거의 확실히 또 실패한다. 그 회복은
 * {@code ContractDraftRecoveryScheduler}(5분 주기)가 맡는다. 매칭이 5xx 를 재시도 목록에서
 * 뺀 것과 같은 판단이다.
 *
 * <p>4xx 는 우리 요청이 틀린 것이라 몇 번을 불러도 같다(예: main_task 빈 값 422).
 *
 * <p><b>이 빈을 지우면 {@code @Retry} 가 resilience4j 기본값(모든 예외 재시도)으로 조용히
 * 돌아간다.</b> 5xx 재시도 금지가 소리 없이 사라진다.
 */
@Configuration
@RequiredArgsConstructor
public class ContractDraftRetryConfig {

    public static final String RETRY_NAME = "contractDraftApi";

    private final RetryRegistry retryRegistry;

    /** 최초 1회 + 재시도 2회. 파이썬 재배포로 연결이 끊기는 상황이 표적이고 그건 대개 몇 초다. */
    @Value("${contract.draft.retry-max-attempts:3}")
    private int maxAttempts;

    @PostConstruct
    void registerContractDraftRetry() {
        retryRegistry.retry(RETRY_NAME, RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(1), 2))
                // 요청이 상대에 닿지도 못한 경우다. Gemini 를 부르기 전이라 재호출 비용이 없다.
                .retryExceptions(ResourceAccessException.class)
                // 위에서 대상을 지정했으므로 사실상 중복이지만, "일부러 뺐다"를 코드에 남긴다.
                .ignoreExceptions(HttpClientErrorException.class, HttpServerErrorException.class)
                .build());
    }
}
