package com.pairing.negotiation.infrastructure.ai;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.pairing.negotiation.application.port.out.NegotiationProposalPort;
import com.pairing.negotiation.domain.service.NegotiationProposalStub;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 협상 제안을 파이썬 AI 서버(/api/v1/negotiations/propose)에서 받는다.
 * 호출 실패·타임아웃 시 {@link NegotiationProposalStub} 로 폴백해 협상 루프가 멈추지 않게 한다.
 * (외부 의존 실패가 핵심 흐름을 끊지 않도록 하는 설계.)
 */
@Slf4j
@Component
public class NegotiationProposalHttpAdapter implements NegotiationProposalPort {

    private static final String PROPOSE_PATH = "/api/v1/negotiations/propose";
    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final int CONNECT_TIMEOUT_MS = 3000;

    private final RestClient restClient;
    private final String internalApiKey;

    public NegotiationProposalHttpAdapter(
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
    public List<Proposal> propose(ProposalContext context) {
        Map<Long, ProposalItem> byId = callPython(context);
        // 파이썬 성공이든 실패든, 요청한 모든 조건에 제안이 있어야 루프가 진행된다.
        // 파이썬이 준 것은 그대로, 빠진 것(또는 전체 실패)은 stub 폴백.
        List<Proposal> result = new ArrayList<>();
        for (ConditionInput condition : context.conditions()) {
            ProposalItem item = byId.get(condition.conditionId());
            if (item != null) {
                result.add(new Proposal(condition.conditionId(), item.proposedValue(),
                        item.content(), item.reason()));
            } else {
                result.add(fallback(condition));
            }
        }
        return result;
    }

    /** 파이썬 호출. 실패 시 빈 맵(→ 전량 폴백). */
    private Map<Long, ProposalItem> callPython(ProposalContext context) {
        try {
            ProposeApiResponse response = restClient.post()
                    .uri(PROPOSE_PATH)
                    .header(INTERNAL_KEY_HEADER, internalApiKey)
                    .headers(headers -> traceId().ifPresent(id -> headers.add(TRACE_ID_HEADER, id)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toRequest(context))
                    .retrieve()
                    .body(ProposeApiResponse.class);

            if (response == null || response.data() == null || response.data().proposals() == null) {
                log.warn("협상 제안 응답이 비어 파이썬 폴백: negotiationId={}", context.negotiationId());
                return Map.of();
            }
            return response.data().proposals().stream()
                    .collect(Collectors.toMap(ProposalItem::conditionId, Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            log.warn("협상 제안 파이썬 호출 실패 → stub 폴백: negotiationId={}, cause={}",
                    context.negotiationId(), e.toString());
            return Map.of();
        }
    }

    private Proposal fallback(ConditionInput condition) {
        NegotiationProposalStub.Proposal p =
                NegotiationProposalStub.propose(condition.clientValue(), condition.freelancerValue());
        return new Proposal(condition.conditionId(), p.value(), p.content(), p.reason());
    }

    private Optional<String> traceId() {
        return Optional.ofNullable(MDC.get("traceId"));
    }

    private ProposeApiRequest toRequest(ProposalContext context) {
        List<ConditionPayload> conditions = context.conditions().stream()
                .map(c -> new ConditionPayload(c.conditionId(),
                        c.type() == null ? null : c.type().name(),
                        c.clientValue(), c.freelancerValue(), c.clientFloor(), c.freelancerFloor()))
                .toList();
        return new ProposeApiRequest(context.negotiationId(), context.round(), context.budgetCap(), conditions);
    }

    // ----- 파이썬 계약 DTO (snake_case) -----

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record ProposeApiRequest(Long negotiationId, Integer round, Long budgetCap,
                                     List<ConditionPayload> conditions) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record ConditionPayload(Long conditionId, String type, String clientValue, String freelancerValue,
                                    String clientFloor, String freelancerFloor) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record ProposeApiResponse(String code, String message, ProposeData data) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record ProposeData(Long negotiationId, String model, List<ProposalItem> proposals) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record ProposalItem(Long conditionId, String proposedValue, String content, String reason) {
    }
}
