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
        STARTED,        // 마지노선 설정 + 초기 제안
        ANSWERED,       // 응답 처리 + 다음 제안
        AGREED,         // 전 조건 합의 타결
        FAILED,         // 포기/자동 결렬
        // 아래 둘은 A2A 비동기화와 함께 생겼다. 동기였을 땐 "도는 중"이라는 관측 가능한 상태가
        // 없었고(응답이 오면 이미 끝난 것), 실패는 HTTP 에러로 사용자에게 바로 갔다.
        AGENT_RUNNING,  // 대리인이 돌기 시작했다. 화면은 진행 표시로 바꾼다
        AGENT_FAILED    // 대리인 호출 실패. 라운드가 오르지 않았다 — 화면에 실패와 재시도를 노출한다
    }
}
