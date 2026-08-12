package com.pairing.support.infrastructure.ai;

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
        return new Answer(data.answer(), data.intent());
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

    /** {@code intent} 는 구버전 AI 서버가 안 내려줄 수 있다. 그 경우 null 이고 NONE 으로 떨어진다. */
    private record AnswerData(String answer, String intent, String model) {
    }
}
