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
 * 협상 A2A 대화를 파이썬 AI 서버(/api/v1/negotiations/propose)에서 받는다.
 * 호출 실패·타임아웃 시 {@link NegotiationProposalStub} 기반 stub A2A 로 폴백해 협상 루프가 멈추지 않게 한다
 * (파이썬 다운·크레딧 소진 등 외부 의존 실패가 핵심 흐름을 끊지 않도록 하는 설계).
 */
@Slf4j
@Component
public class NegotiationProposalHttpAdapter implements NegotiationProposalPort {

    private static final String PROPOSE_PATH = "/api/v1/negotiations/propose";
    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final int CONNECT_TIMEOUT_MS = 3000;

    private static final String CLIENT_AGENT = "CLIENT_AGENT";
    private static final String FREELANCER_AGENT = "FREELANCER_AGENT";

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
    public A2AResult propose(ProposalContext context) {
        ProposeData data = callPython(context);
        Map<Long, OutcomeItem> outcomeById = data == null || data.outcomes() == null
                ? Map.of()
                : data.outcomes().stream()
                        .collect(Collectors.toMap(OutcomeItem::conditionId, Function.identity(), (a, b) -> a));

        List<AgentMessage> messages = new ArrayList<>();
        List<ConditionOutcome> outcomes = new ArrayList<>();

        // 파이썬이 준 대화는 그대로 싣되(요청 조건에 한함), 파이썬이 결과를 못 준 조건은 stub A2A 로 채운다.
        List<AgentMessage> pythonMessages = data == null || data.messages() == null
                ? List.of()
                : data.messages().stream()
                        .filter(m -> containsCondition(context, m.conditionId()))
                        .map(m -> new AgentMessage(m.sender(), m.conditionId(), m.kind(),
                                m.proposedValue(), m.content(), m.reason()))
                        .toList();

        for (ConditionInput condition : context.conditions()) {
            OutcomeItem item = outcomeById.get(condition.conditionId());
            if (item != null) {
                // 이 조건의 파이썬 대화 + 결과 사용.
                pythonMessages.stream()
                        .filter(m -> condition.conditionId().equals(m.conditionId()))
                        .forEach(messages::add);
                outcomes.add(new ConditionOutcome(condition.conditionId(), item.proposedValue(), item.agreed()));
            } else {
                // 파이썬이 이 조건 결과를 못 줌 → stub A2A 폴백.
                StubExchange stub = stubExchange(condition);
                messages.addAll(stub.messages());
                outcomes.add(stub.outcome());
            }
        }
        return new A2AResult(messages, outcomes);
    }

    /** 파이썬 호출. 실패 시 null(→ 전량 stub 폴백). */
    private ProposeData callPython(ProposalContext context) {
        try {
            ProposeApiResponse response = restClient.post()
                    .uri(PROPOSE_PATH)
                    .header(INTERNAL_KEY_HEADER, internalApiKey)
                    .headers(headers -> traceId().ifPresent(id -> headers.add(TRACE_ID_HEADER, id)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toRequest(context))
                    .retrieve()
                    .body(ProposeApiResponse.class);

            if (response == null || response.data() == null || response.data().outcomes() == null) {
                log.warn("협상 A2A 응답이 비어 stub 폴백: negotiationId={}", context.negotiationId());
                return null;
            }
            return response.data();
        } catch (Exception e) {
            log.warn("협상 A2A 파이썬 호출 실패 → stub 폴백: negotiationId={}, cause={}",
                    context.negotiationId(), e.toString());
            return null;
        }
    }

    /** 파이썬 없이도 대화 로그가 나오도록, 조건당 [클라 제안 → 프리 수락] 2턴 + 합의 결과를 만든다. */
    private StubExchange stubExchange(ConditionInput condition) {
        NegotiationProposalStub.Proposal p =
                NegotiationProposalStub.propose(condition.clientValue(), condition.freelancerValue());
        List<AgentMessage> messages = List.of(
                new AgentMessage(CLIENT_AGENT, condition.conditionId(), "PROPOSAL",
                        p.value(), p.content(), p.reason()),
                new AgentMessage(FREELANCER_AGENT, condition.conditionId(), "ACCEPT",
                        p.value(), p.value() + " 를 수락합니다.", "제시값을 수용합니다. (stub)"));
        return new StubExchange(messages, new ConditionOutcome(condition.conditionId(), p.value(), true));
    }

    private record StubExchange(List<AgentMessage> messages, ConditionOutcome outcome) {
    }

    private boolean containsCondition(ProposalContext context, Long conditionId) {
        return context.conditions().stream().anyMatch(c -> c.conditionId().equals(conditionId));
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
    private record ProposeData(Long negotiationId, String model,
                               List<MessageItem> messages, List<OutcomeItem> outcomes) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record MessageItem(String sender, Long conditionId, String kind, String proposedValue,
                               String content, String reason) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record OutcomeItem(Long conditionId, String proposedValue, boolean agreed) {
    }
}
