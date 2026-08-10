package com.pairing.support.infrastructure.mapper;

import com.pairing.support.domain.model.ChatbotSession;
import com.pairing.support.infrastructure.persistence.ChatbotSessionJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ChatbotSessionMapper {

    ChatbotSessionJpaEntity toJpaEntity(ChatbotSession session);

    default ChatbotSession toDomain(ChatbotSessionJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return ChatbotSession.reconstitute(entity.getId(), entity.getOwnerAccountId(), entity.getCreatedAt());
    }
}
