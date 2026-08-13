package com.pairing.contract.infrastructure.ai;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.pairing.contract.application.port.ContractDraftPort;
import com.pairing.contract.domain.model.ContractDraftText;
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
 * <p>호출 실패·타임아웃이면 {@code Optional.empty()} 를 돌려주고 호출부가 원문을 잘라 쓴다.
 * 외부 의존 실패가 계약 체결을 끊지 않게 하려는 것으로, 협상 제안 어댑터와 같은 방식이다.
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
            @Value("${app.ai.timeout-ms:20000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.internalApiKey = internalApiKey;
    }

    @Override
    public Optional<ContractDraftText> draft(ContractDraftCommand command) {
        try {
            DraftApiResponse response = restClient.post()
                    .uri(DRAFT_PATH)
                    .header(INTERNAL_KEY_HEADER, internalApiKey)
                    .headers(headers -> traceId().ifPresent(id -> headers.add(TRACE_ID_HEADER, id)))
                    .contentType(MediaType.APPLICATION_JSON)
                    // Accept 를 안 보내면 상대가 application/octet-stream 으로 내려줄 수 있고, 그러면
                    // 이 응답을 읽을 컨버터가 없어 예외가 난다. 협상 어댑터에서 실제로 겪은
                    // 실패다(2026-08-11 실측). 여기서는 기본 문구로 조용히 대체되므로,
                    // AI 문구가 아예 안 붙은 계약서가 나가고도 아무도 눈치채지 못한다.
                    .accept(MediaType.APPLICATION_JSON)
                    .body(toRequest(command))
                    .retrieve()
                    .body(DraftApiResponse.class);

            if (response == null || response.data() == null) {
                log.warn("계약서 문구 응답이 비었습니다. 기본 문구로 진행합니다: contractId={}",
                        command.contractId());
                return Optional.empty();
            }
            return Optional.of(response.data().toDomain());

        } catch (Exception e) {
            log.warn("계약서 문구 파이썬 호출 실패. 기본 문구로 진행합니다: contractId={}, cause={}",
                    command.contractId(), e.toString());
            return Optional.empty();
        }
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
