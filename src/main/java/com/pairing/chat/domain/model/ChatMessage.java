package com.pairing.chat.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 채팅 메시지 1건. 방 애그리거트와 분리된 고빈도 엔티티다(협상 메시지와 같은 이유).
 *
 * <p>{@code senderId} 가 null 이면 시스템 안내(방 생성 알림 등)다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    private Long id;
    private Long chatRoomId;
    private Long senderId;          // null 이면 시스템 메시지
    private ChatMessageType messageType;
    private String content;
    private LocalDateTime createdAt;

    private ChatMessage(Long id, Long chatRoomId, Long senderId, ChatMessageType messageType,
                        String content, LocalDateTime createdAt) {
        this.id = id;
        this.chatRoomId = chatRoomId;
        this.senderId = senderId;
        this.messageType = messageType;
        this.content = content;
        this.createdAt = createdAt;
    }

    /** 사용자 발화. */
    public static ChatMessage text(Long chatRoomId, Long senderId, String content) {
        return new ChatMessage(null, chatRoomId, senderId, ChatMessageType.TEXT, content, LocalDateTime.now());
    }

    /** 시스템 안내(방 생성·종료 등). 보낸 사람 없음. */
    public static ChatMessage system(Long chatRoomId, String content) {
        return new ChatMessage(null, chatRoomId, null, ChatMessageType.SYSTEM, content, LocalDateTime.now());
    }

    public static ChatMessage reconstitute(Long id, Long chatRoomId, Long senderId,
                                           ChatMessageType messageType, String content,
                                           LocalDateTime createdAt) {
        return new ChatMessage(id, chatRoomId, senderId, messageType, content, createdAt);
    }

    public boolean isSentBy(Long accountId) {
        return senderId != null && senderId.equals(accountId);
    }
}
