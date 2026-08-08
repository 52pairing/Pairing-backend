package com.pairing.chat.infrastructure.mapper;

import com.pairing.chat.domain.model.ChatMessage;
import com.pairing.chat.infrastructure.persistence.ChatMessageJpaEntity;
import org.springframework.stereotype.Component;

/** 채팅 메시지 도메인 ↔ JPA 매핑. */
@Component
public class ChatMessageMapper {

    public ChatMessageJpaEntity toJpaEntity(ChatMessage m) {
        return new ChatMessageJpaEntity(m.getId(), m.getChatRoomId(), m.getSenderId(), m.getMessageType(),
                m.getContent(), m.getCreatedAt());
    }

    public ChatMessage toDomain(ChatMessageJpaEntity e) {
        if (e == null) {
            return null;
        }
        return ChatMessage.reconstitute(e.getId(), e.getChatRoomId(), e.getSenderId(), e.getMessageType(),
                e.getContent(), e.getCreatedAt());
    }
}
