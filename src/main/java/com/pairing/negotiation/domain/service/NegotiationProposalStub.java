package com.pairing.negotiation.domain.service;

/**
 * 협상 제안 생성 stub. 파이썬 AI 제안이 실패할 때의 **폴백**이다(발표·운영 중 파이썬 다운 대비).
 * 숫자 조건은 양측 값의 중간값, 그 외는 클라 값을 제안한다.
 */
public final class NegotiationProposalStub {

    private NegotiationProposalStub() {
    }

    public record Proposal(String value, String content, String reason) {
    }

    /** 공개 희망값(클라/프리)만으로 결정적 제안을 만든다. */
    public static Proposal propose(String clientValue, String freelancerValue) {
        Long client = tryParse(clientValue);
        Long freelancer = tryParse(freelancerValue);

        if (client != null && freelancer != null) {
            long mid = (client + freelancer) / 2;
            return new Proposal(String.valueOf(mid),
                    "중간값 " + mid + " 를 제안합니다.",
                    "양측 값의 중간값입니다. (stub)");
        }

        String value = clientValue != null ? clientValue : freelancerValue;
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
