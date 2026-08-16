package com.pairing.contract.infrastructure.ai;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.pairing.contract.application.port.ContractDraftPort;
import com.pairing.contract.domain.model.ContractDraftText;
import com.pairing.contract.exception.ContractErrorCode;
import com.pairing.contract.infrastructure.config.ContractDraftRetryConfig;
import com.pairing.global.exception.BusinessException;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * 계약서 문구를 파이썬 AI 서버(/api/v1/contracts/draft-texts)에서 받는다.
 *
 * <p><b>실패를 삼키지 않는다.</b> 예외가 그대로 올라가야 {@code @Retry} 가 재시도하고,
 * 호출부가 계약을 DRAFT 에 남길 수 있다. 예전처럼 원문으로 대체해 버리면 덜 다듬어진
 * 계약서가 서명 단계로 넘어간다.
 */
@Slf4j
@Component
public class ContractDraftAdapter implements ContractDraftPort {

    private static final String DRAFT_PATH = "/api/v1/contracts/draft-texts";
    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final int CONNECT_TIMEOUT_MS = 3000;

    private final RestClient restClient;
    private final String internalApiKey;

    public ContractDraftAdapter(
            @Value("${app.ai.base-url:http://localhost:8000}") String baseUrl,
            @Value("${app.ai.internal-api-key:}") String internalApiKey,
            @Value("${app.ai.timeout-ms:120000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.internalApiKey = internalApiKey;
    }

    /**
     * <p><b>{@code fallbackMethod} 를 두지 않는다.</b> 폴백을 두면 실패가 정상 반환으로 바뀌어
     * 재시도를 걸 자리가 다시 사라진다. 그게 지금 고치려는 문제다.
     *
     * <p>재시도 대상은 연결 실패뿐이다({@link ContractDraftRetryConfig}). 5xx 는
     * 파이썬이 이미 Gemini 를 재시도한 뒤라 곧바로 다시 불러도 성공 확률이 오르지 않는다.
     * 그쪽 회복은 5분 주기 스케줄러가 맡는다.
     */
    @Override
    @Retry(name = ContractDraftRetryConfig.RETRY_NAME)
    public ContractDraftText draft(ContractDraftCommand command) {
        DraftApiResponse response = restClient.post()
                .uri(DRAFT_PATH)
                .header(INTERNAL_KEY_HEADER, internalApiKey)
                .headers(headers -> traceId().ifPresent(id -> headers.add(TRACE_ID_HEADER, id)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(toRequest(command))
                .retrieve()
                .body(DraftApiResponse.class);

        // 2xx 인데 본문이 비었다. 다시 부르면 성공할 수 있으므로 실패로 다룬다.
        if (response == null || response.data() == null) {
            log.warn("계약서 문구 응답이 비었습니다: contractId={}", command.contractId());
            throw new BusinessException(ContractErrorCode.DRAFT_TEXT_UNAVAILABLE);
        }
        return response.data().toDomain();
    }

    private Optional<String> traceId() {
        return Optional.ofNullable(MDC.get("traceId"));
    }

    private DraftApiRequest toRequest(ContractDraftCommand command) {
        List<String> notes = command.agreedNotes() == null ? List.of() : command.agreedNotes();
        return new DraftApiRequest(command.contractId(), command.mainTask(),
                command.detailScope(), notes);
    }

    // ----- 파이썬 계약 DTO (snake_case) -----

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record DraftApiRequest(Long contractId, String mainTask, String detailScope,
                                   List<String> agreedNotes) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record DraftApiResponse(String code, String message, DraftData data) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record DraftData(Long contractId, String model, String mainTaskSummary,
                             String detailScopeSummary, String specialTerms) {

        ContractDraftText toDomain() {
            return new ContractDraftText(mainTaskSummary, detailScopeSummary, specialTerms);
        }
    }
}
