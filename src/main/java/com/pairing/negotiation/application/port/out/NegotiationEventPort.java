package com.pairing.negotiation.application.port.out;

import com.pairing.negotiation.application.event.NegotiationEvent;

/**
 * 협상 실시간 이벤트 발행 포트. 구현은 STOMP 브로드캐스트.
 * 트랜잭션 커밋 이후에 발행해야 클라 재조회가 최신 상태를 본다(구현체 책임).
 */
public interface NegotiationEventPort {

    void publish(NegotiationEvent event);
}
