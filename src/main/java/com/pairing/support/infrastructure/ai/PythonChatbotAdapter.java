package com.pairing.support.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pairing.global.exception.BusinessException;
import com.pairing.global.filter.TraceIdFilter;
import com.pairing.support.application.port.out.ChatbotAiPort;
import com.pairing.support.exception.ChatbotErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Pairing-python(FastAPI, AI 서버) 챗봇 호출 어댑터.
 *
 * <p>계약은 {@code Pairing-python/README.md} "3. 스프링 ↔ AI 서버 통신 규약" 절 기준이고,
 * 매칭 도메인의 {@code PythonMatchingAdapter}와 동일한 패턴이다.
 */
@Slf4j
@Component
public class PythonChatbotAdapter implements ChatbotAiPort {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final RestClient restClient;
    private final String internalApiKey;

    public PythonChatbotAdapter(@Value("${ai.pairing-python.base-url}") String baseUrl,
                                @Value("${ai.pairing-python.internal-api-key}") String internalApiKey) {
        this.internalApiKey = internalApiKey;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    @CircuitBreaker(name = "pythonChatbotApi", fallbackMethod = "askFallback")
    public Answer ask(String question) {
        Map<String, Object> requestBody = Map.of("question", question);

        PythonApiResponse<AnswerData> response = restClient.post()
                .uri("/api/v1/chatbot/answer")
                .headers(this::withCommonHeaders)
                .body(requestBody)
                .retrieve()
                .body(new ParameterizedTypeReference<PythonApiResponse<AnswerData>>() {
                });

        AnswerData data = requireData(response);
        // 구버전 AI 서버는 charge_quota 를 안 내려준다. 그때는 차감하는 쪽(기존 동작)이 기본이다.
        return new Answer(data.answer(), data.intent(), !Boolean.FALSE.equals(data.chargeQuota()));
    }

    /**
     * 재색인은 서킷브레이커를 걸지 않는다. 사람이 눌러서 한 번 도는 운영 작업이라, 실패하면
     * 그 자리에서 에러를 보고 다시 누르면 된다. 여기서 서킷이 열리면 <b>질의 쪽 서킷과 통계가
     * 섞여</b> 챗봇 전체가 막힌 것처럼 보인다.
     */
    @Override
    public KnowledgeReindexResult reindexKnowledge() {
        PythonApiResponse<ReindexData> response = restClient.post()
                .uri("/api/v1/chatbot/knowledge/reindex")
                .headers(this::withCommonHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<PythonApiResponse<ReindexData>>() {
                });

        if (response == null || response.data() == null) {
            log.warn("[Pairing-python] 지식 재색인 응답 data 가 비어 있습니다.");
            throw new BusinessException(ChatbotErrorCode.AI_SERVER_CALL_FAILED);
        }
        ReindexData data = response.data();
        log.info("[Pairing-python] 챗봇 지식 재색인 완료 — 전체 {}건, 신규·변경 {}건, 유지 {}건",
                data.total(), data.embedded(), data.skipped());
        return new KnowledgeReindexResult(data.total(), data.embedded(), data.skipped());
    }

    private void withCommonHeaders(HttpHeaders headers) {
        headers.add(INTERNAL_API_KEY_HEADER, internalApiKey);
        headers.add(TRACE_ID_HEADER, TraceIdFilter.currentTraceId());
        // Accept 를 안 보내면 상대가 application/octet-stream 으로 내려줄 수 있고, 그러면 이 응답을
        // 읽을 컨버터가 없어 예외가 난다. 협상 어댑터에서 실제로 겪은 실패다(2026-08-11 실측).
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    }

    private AnswerData requireData(PythonApiResponse<AnswerData> response) {
        if (response == null || response.data() == null) {
            log.warn("[Pairing-python] 챗봇 응답 data가 비어 있습니다.");
            throw new BusinessException(ChatbotErrorCode.AI_SERVER_CALL_FAILED);
        }
        return response.data();
    }

    /** AI 서버 장애/서킷 오픈 시 폴백. */
    private Answer askFallback(String question, Throwable t) {
        log.error("[Pairing-python] 챗봇 호출 실패/서킷 오픈 (원인: {})", t.getMessage());
        throw new BusinessException(ChatbotErrorCode.AI_SERVER_CALL_FAILED);
    }

    private record PythonApiResponse<T>(String code, String message, T data) {
    }

    /** 재색인 결과. 이 응답은 한 단어 필드뿐이라 snake_case 매핑이 필요 없다. */
    private record ReindexData(int total, int embedded, int skipped) {
    }

    /**
     * {@code intent}·{@code chargeQuota} 는 구버전 AI 서버가 안 내려줄 수 있다.
     * 그 경우 각각 NONE·차감으로 떨어져 기존 동작 그대로다.
     *
     * <p>{@code out_of_scope} 는 받지 않는다. 차감 판단은 {@code charge_quota} 하나로 끝나고,
     * 범위 밖이라는 사실 자체는 AI 서버 로그에 남는다. 여기서 또 들고 있으면 둘이 어긋날 때
     * 어느 쪽을 믿어야 하는지가 불분명해진다.
     *
     * <p>AI 서버 응답은 snake_case 라 {@code @JsonProperty} 로 매핑한다. 전역 Jackson 설정은
     * 건드리지 않는다({@code PythonMatchingAdapter} 와 같은 방식).
     */
    private record AnswerData(
            String answer,
            String intent,
            String model,
            @JsonProperty("charge_quota") Boolean chargeQuota
    ) {
    }
}
