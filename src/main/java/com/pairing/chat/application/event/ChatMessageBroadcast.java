package com.pairing.chat.application.event;

import com.pairing.chat.domain.model.ChatMessageType;

import java.time.LocalDateTime;

/**
 * 새 채팅 메시지 실시간 알림. 구독 경로 {@code /topic/chat-rooms/{chatRoomId}} 로 나간다.
 *
 * <p>"내가 보낸 메시지인지(mine)"는 수신자마다 다르므로 담지 않는다. 프론트가 {@code senderId} 를
 * 자기 계정과 비교해 판단한다.
 */
public record ChatMessageBroadcast(
        Long chatRoomId,
        Long messageId,
        Long senderId,
        String senderName,
        ChatMessageType messageType,
        String content,
        LocalDateTime createdAt
) {
}
