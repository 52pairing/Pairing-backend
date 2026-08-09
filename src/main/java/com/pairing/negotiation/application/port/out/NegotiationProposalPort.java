package com.pairing.negotiation.application.port.out;

import com.pairing.negotiation.domain.model.ConditionType;

import java.util.List;

/**
 * 협상 제안 생성 포트(A2A). 구현은 파이썬 AI 서버(/negotiations/propose) 호출이며, 실패 시 stub 로 폴백한다.
 *
 * <p>두 대리인(클라이언트 대리 / 프리랜서 대리)이 제안·역제안·수락을 주고받는 대화를 생성한다.
 * 백엔드(심판)는 그 대화를 로그로 기록하고, 대리인이 합의한 조건은 자동 락, 나머지는 사람 승인/재지시로 넘긴다.
 */
public interface NegotiationProposalPort {

    /** 미합의 조건들에 대해 A2A 대화와 쟁점별 결과를 만든다. */
    A2AResult propose(ProposalContext context);

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

    /** A2A 결과: 대화 로그(시간순) + 쟁점별 최종 결과. */
    record A2AResult(List<AgentMessage> messages, List<ConditionOutcome> outcomes) {
    }

    /** 대화 한 줄. sender 는 CLIENT_AGENT / FREELANCER_AGENT. */
    record AgentMessage(
            String sender,
            Long conditionId,
            String kind,            // PROPOSAL / COUNTER / ACCEPT (표시용)
            String proposedValue,
            String content,
            String reason
    ) {
    }

    /** 쟁점별 결과. agreed=true 면 두 대리인이 합의(자동 락 후보). */
    record ConditionOutcome(Long conditionId, String proposedValue, boolean agreed) {
    }
}
