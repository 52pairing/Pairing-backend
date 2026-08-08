package com.pairing.matching.application.result;

/** 매칭 요청 상세 카드에 표시할 협상 진행 정보. negotiation 도메인 소유 데이터. */
public record NegotiationSummary(Long negotiationId, int currentRound, int maxRound, int newProposalCount) {
}
