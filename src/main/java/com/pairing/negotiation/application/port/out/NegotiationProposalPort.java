package com.pairing.negotiation.application.port.out;

import com.pairing.negotiation.domain.model.ConditionType;

import java.util.List;

/**
 * 협상 제안 생성 포트. 구현은 파이썬 AI 서버(/negotiations/propose) 호출이며, 실패 시 stub 로 폴백한다.
 * 백엔드(심판)가 라운드/락/타결을 확정하고, 여기서는 "제안값·근거"만 얻는다.
 */
public interface NegotiationProposalPort {

    /** 미합의 조건들에 대한 제안을 한 번에 만든다. 반환은 conditionId 별 제안. */
    List<Proposal> propose(ProposalContext context);

    record ProposalContext(Long negotiationId, int round, Long budgetCap, List<ConditionInput> conditions) {
    }

    record ConditionInput(
            Long conditionId,
            ConditionType type,
            String clientValue,
            String freelancerValue,
            String clientFloor,      // 비공개 마지노선(내부 호출이라 참고용으로 전달)
            String freelancerFloor
    ) {
    }

    record Proposal(Long conditionId, String proposedValue, String content, String reason) {
    }
}
