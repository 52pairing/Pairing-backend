package com.pairing.chat.application.port.out;

import com.pairing.chat.application.event.ChatMessageBroadcast;

/** 채팅 실시간 이벤트 발행 포트. 구현체가 STOMP {@code /topic/chat-rooms/{id}} 로 broadcast 한다. */
public interface ChatEventPort {

    void publish(ChatMessageBroadcast event);
}
