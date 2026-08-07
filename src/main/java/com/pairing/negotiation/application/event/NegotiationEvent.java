package com.pairing.negotiation.application.event;

import com.pairing.negotiation.domain.model.NegotiationStatus;

/**
 * 협상 실시간 이벤트(브로드캐스트용). **민감정보(마지노선 등) 없음** — "바뀌었다"는 신호만 담는다.
 * 클라이언트는 이 이벤트를 받고 {@code GET /negotiations/{id}} 로 각자 마스킹된 상세를 재조회한다.
 */
public record NegotiationEvent(
        Long negotiationId,
        NegotiationEventType type,
        NegotiationStatus status,
        int totalRound
) {

    public enum NegotiationEventType {
        STARTED,    // 마지노선 설정 + 초기 제안
        ANSWERED,   // 응답 처리 + 다음 제안
        AGREED,     // 전 조건 합의 타결
        FAILED      // 포기/자동 결렬
    }
}
