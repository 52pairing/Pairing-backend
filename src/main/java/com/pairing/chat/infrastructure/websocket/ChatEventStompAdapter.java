package com.pairing.chat.infrastructure.websocket;

import com.pairing.chat.application.event.ChatMessageBroadcast;
import com.pairing.chat.application.port.out.ChatEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 새 채팅 메시지를 STOMP 로 broadcast 한다. 구독 경로 {@code /topic/chat-rooms/{id}}.
 *
 * <p>협상 이벤트와 같은 이유로 **커밋 이후**에 발행한다(트랜잭션 진행 중이면). 커밋 전에 쏘면
 * 아직 저장되지 않은 메시지를 클라가 조회할 수 있는 레이스가 생긴다.
 */
@Component
@RequiredArgsConstructor
public class ChatEventStompAdapter implements ChatEventPort {

    private static final String TOPIC_PREFIX = "/topic/chat-rooms/";

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publish(ChatMessageBroadcast event) {
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

    private void send(ChatMessageBroadcast event) {
        messagingTemplate.convertAndSend(TOPIC_PREFIX + event.chatRoomId(), event);
    }
}
