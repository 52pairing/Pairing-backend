package com.pairing.negotiation.infrastructure.ai;

import com.pairing.negotiation.application.port.out.NegotiationProposalPort;
import com.pairing.negotiation.application.port.out.NegotiationProposalPort.ConditionInput;
import com.pairing.negotiation.application.port.out.NegotiationProposalPort.ProposalContext;
import com.pairing.negotiation.domain.model.ConditionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 파이썬 서버가 없을 때 stub 으로 폴백해 모든 조건에 제안이 채워지는지(루프가 안 멈추는지) 검증. */
class NegotiationProposalHttpAdapterTest {

    @Test
    @DisplayName("파이썬 호출 실패 시 stub 폴백 — 모든 조건에 제안이 채워진다")
    void fallsBackToStubWhenPythonUnreachable() {
        // 아무도 안 듣는 주소 → 연결 거부 → 폴백. 타임아웃 짧게.
        NegotiationProposalHttpAdapter adapter =
                new NegotiationProposalHttpAdapter("http://localhost:1", "test-key", 500);

        ProposalContext context = new ProposalContext(1L, 1, 5_000_000L, List.of(
                new ConditionInput(401L, ConditionType.AMOUNT, "4000000", "6000000", null, null),
                new ConditionInput(402L, ConditionType.WORK_STYLE, "ONSITE", "REMOTE", null, null)));

        List<NegotiationProposalPort.Proposal> proposals = adapter.propose(context);

        assertThat(proposals).hasSize(2);
        assertThat(proposals).extracting(NegotiationProposalPort.Proposal::conditionId)
                .containsExactlyInAnyOrder(401L, 402L);
        // AMOUNT 는 숫자라 중간값(stub)
        assertThat(proposals.stream()
                .filter(p -> p.conditionId().equals(401L)).findFirst().orElseThrow().proposedValue())
                .isEqualTo("5000000");
    }
}
