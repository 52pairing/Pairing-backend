package com.pairing.negotiation.application.event;

import com.pairing.negotiation.application.event.NegotiationEvent.NegotiationEventType;

/**
 * 대리인(A2A)을 돌려야 한다는 내부 신호. <b>스프링 애플리케이션 이벤트</b>이지 실시간 브로드캐스트가
 * 아니다 — 화면으로 나가는 것은 {@link NegotiationEvent} 다.
 *
 * <p>{@code NegotiationAgentListener} 가 커밋 후 별도 스레드에서 받는다. 커밋 <b>후</b>여야 하는
 * 이유는 리스너가 새 트랜잭션에서 협상을 다시 읽기 때문이다 — 아직 커밋 전이면 방금 저장한
 * 마지노선·응답이 안 보인다.
 *
 * @param negotiationId 대상 협상
 * @param fallbackType  타결/결렬이 아닐 때 실시간으로 내보낼 이벤트 종류.
 *                      {@code /start} 는 {@code STARTED}, {@code /answers} 는 {@code ANSWERED}
 */
public record NegotiationAgentRequested(
        Long negotiationId,
        NegotiationEventType fallbackType
) {
}
