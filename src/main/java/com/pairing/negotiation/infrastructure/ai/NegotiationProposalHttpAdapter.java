package com.pairing.negotiation.infrastructure.ai;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.pairing.negotiation.application.port.out.NegotiationProposalPort;
import com.pairing.negotiation.domain.service.NegotiationAgreedValueNormalizer;
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
                // 이 조건의 파이썬 대화 + 결과 사용. 값은 계약 표기로 정규화해 싣는다.
                List<AgentMessage> conditionMessages = pythonMessages.stream()
                        .filter(m -> condition.conditionId().equals(m.conditionId()))
                        .map(m -> normalizeMessage(m, condition))
                        .toList();
                messages.addAll(conditionMessages);
                outcomes.add(normalizeOutcome(item, condition, lastProposedValue(conditionMessages)));
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

    /**
     * 합의 결과를 계약 표기로 정규화한다. 해석할 수 없는 값(없는 enum·형식 이탈)이면 <b>합의로 인정하지 않는다</b>
     * — 그대로 락하면 계약 단계에서 못 읽는 값이 굳어지므로, 사람 승인 패널로 넘긴다.
     */
    private ConditionOutcome normalizeOutcome(OutcomeItem item, ConditionInput condition,
                                              String lastDialogueValue) {
        Optional<String> normalized = NegotiationAgreedValueNormalizer.normalize(
                condition.type(), item.proposedValue(), reference(condition));
        if (normalized.isEmpty()) {
            if (item.agreed()) {
                log.warn("A2A 합의값을 해석할 수 없어 합의 취소(사람 승인으로 이관): conditionId={}, type={}, value={}",
                        condition.conditionId(), condition.type(), item.proposedValue());
            }
            return new ConditionOutcome(condition.conditionId(), item.proposedValue(), false);
        }

        String value = normalized.get();
        // 대화의 결론과 결과값이 다르면 합의로 인정하지 않는다.
        // 화면에는 "330만에 합의했습니다"라고 찍히는데 실제로는 480만이 락되는 사례를 확인했다.
        // 사람이 읽은 것과 다른 값이 계약으로 넘어가는 게 최악이라, 이럴 땐 사람 승인으로 넘긴다.
        if (item.agreed() && lastDialogueValue != null && !lastDialogueValue.equals(value)) {
            log.warn("대화 결론과 결과값이 달라 합의로 인정하지 않음: conditionId={}, 대화={}, 결과={}",
                    condition.conditionId(), lastDialogueValue, value);
            return new ConditionOutcome(condition.conditionId(), value, false);
        }
        return new ConditionOutcome(condition.conditionId(), value, item.agreed());
    }

    /** 이 조건 대화의 마지막 제안값(= 화면에 최종으로 보이는 값). 없으면 null. */
    private String lastProposedValue(List<AgentMessage> conditionMessages) {
        for (int i = conditionMessages.size() - 1; i >= 0; i--) {
            String value = conditionMessages.get(i).proposedValue();
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 대화 값도 정규화한다. 사람이 제안을 수락하면 최신 제안값이 그대로 락되기 때문
     * ({@code NegotiationLoopService} 의 수락 경로). 해석 불가면 대화 맥락 보존을 위해 원문을 남긴다.
     */
    private AgentMessage normalizeMessage(AgentMessage message, ConditionInput condition) {
        String value = NegotiationAgreedValueNormalizer
                .normalize(condition.type(), message.proposedValue(), reference(condition))
                .orElse(message.proposedValue());
        return new AgentMessage(message.sender(), message.conditionId(), message.kind(),
                value, message.content(), message.reason());
    }

    /** PERIOD 처럼 단위가 빠졌을 때 복원 기준이 되는 기존 값. */
    private String reference(ConditionInput condition) {
        return condition.clientValue() != null ? condition.clientValue() : condition.freelancerValue();
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
        // 선택형 허용값·값형식을 함께 준다. 안 주면 LLM 이 없는 값(HYBRID 등)이나 단위 빠진 값을 만든다.
        List<ConditionPayload> conditions = context.conditions().stream()
                .map(c -> new ConditionPayload(c.conditionId(),
                        c.type() == null ? null : c.type().name(),
                        c.clientValue(), c.freelancerValue(), c.clientFloor(), c.freelancerFloor(),
                        emptyToNull(NegotiationAgreedValueNormalizer.allowedValues(c.type())),
                        NegotiationAgreedValueNormalizer.valueFormat(c.type())))
                .toList();
        return new ProposeApiRequest(context.negotiationId(), context.round(), context.budgetCap(), conditions);
    }

    private List<String> emptyToNull(List<String> values) {
        return values == null || values.isEmpty() ? null : values;
    }

    // ----- 파이썬 계약 DTO (snake_case) -----

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record ProposeApiRequest(Long negotiationId, Integer round, Long budgetCap,
                                     List<ConditionPayload> conditions) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record ConditionPayload(Long conditionId, String type, String clientValue, String freelancerValue,
                                    String clientFloor, String freelancerFloor,
                                    List<String> allowedValues, String valueFormat) {
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
