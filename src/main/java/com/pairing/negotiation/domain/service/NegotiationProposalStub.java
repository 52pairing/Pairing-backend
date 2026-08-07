package com.pairing.negotiation.domain.service;

import com.pairing.negotiation.domain.model.NegotiationCondition;

/**
 * 협상 제안 생성 stub. 실제 A2A/파이썬 AI 제안 전에 루프를 돌리기 위한 결정적 대체물이다.
 * (숫자 조건은 양측 값의 중간값, 그 외는 클라 값을 제안한다.)
 *
 * <p>파이썬 연동(후속 슬라이스) 시 이 클래스만 실 제안기로 교체한다.
 */
public final class NegotiationProposalStub {

    private NegotiationProposalStub() {
    }

    public record Proposal(String value, String content, String reason) {
    }

    public static Proposal propose(NegotiationCondition condition) {
        Long client = tryParse(condition.getClientValue());
        Long freelancer = tryParse(condition.getFreelancerValue());

        if (client != null && freelancer != null) {
            long mid = (client + freelancer) / 2;
            return new Proposal(String.valueOf(mid),
                    "중간값 " + mid + " 를 제안합니다.",
                    "양측 값의 중간값입니다. (stub)");
        }

        String value = condition.getClientValue() != null
                ? condition.getClientValue() : condition.getFreelancerValue();
        return new Proposal(value, value + " 를 제안합니다.", "초기 제안값입니다. (stub)");
    }

    private static Long tryParse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
