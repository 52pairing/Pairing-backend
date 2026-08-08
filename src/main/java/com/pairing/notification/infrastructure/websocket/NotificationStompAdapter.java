package com.pairing.notification.infrastructure.websocket;

import com.pairing.notification.application.event.NotificationEvent;
import com.pairing.notification.application.port.out.NotificationEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 새 알림을 STOMP 로 push 한다. 구독 경로 {@code /topic/users/{accountId}/notifications}.
 *
 * <p>채팅·협상 이벤트와 같은 이유로 **커밋 이후**에 발행한다(트랜잭션 진행 중이면). 커밋 전에 쏘면
 * 아직 저장되지 않은 알림을 클라가 조회할 수 있는 레이스가 생긴다.
 */
@Component
@RequiredArgsConstructor
public class NotificationStompAdapter implements NotificationEventPort {

    private static final String TOPIC_FORMAT = "/topic/users/%d/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publish(Long ownerAccountId, NotificationEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(ownerAccountId, event);
                }
            });
        } else {
            send(ownerAccountId, event);
        }
    }

    private void send(Long ownerAccountId, NotificationEvent event) {
        messagingTemplate.convertAndSend(TOPIC_FORMAT.formatted(ownerAccountId), event);
    }
}
