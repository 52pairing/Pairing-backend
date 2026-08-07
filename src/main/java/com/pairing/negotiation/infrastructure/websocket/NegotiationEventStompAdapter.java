package com.pairing.negotiation.infrastructure.websocket;

import com.pairing.negotiation.application.event.NegotiationEvent;
import com.pairing.negotiation.application.port.out.NegotiationEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 협상 이벤트를 STOMP 로 브로드캐스트한다. 구독 경로 {@code /topic/negotiations/{id}}.
 *
 * <p>트랜잭션이 진행 중이면 **커밋 이후**에 발행한다. (커밋 전에 쏘면 클라 재조회가 아직 반영 안 된
 * 상태를 볼 수 있는 레이스 발생.) 트랜잭션이 없으면 즉시 발행한다.
 */
@Component
@RequiredArgsConstructor
public class NegotiationEventStompAdapter implements NegotiationEventPort {

    private static final String TOPIC_PREFIX = "/topic/negotiations/";

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publish(NegotiationEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
        } else {
            send(event);
        }
    }

    private void send(NegotiationEvent event) {
        messagingTemplate.convertAndSend(TOPIC_PREFIX + event.negotiationId(), event);
    }
}
