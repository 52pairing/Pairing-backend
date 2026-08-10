package com.pairing.support.infrastructure.mapper;

import com.pairing.support.domain.model.ChatbotMessage;
import com.pairing.support.infrastructure.persistence.ChatbotMessageJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ChatbotMessageMapper {

    ChatbotMessageJpaEntity toJpaEntity(ChatbotMessage message);

    default ChatbotMessage toDomain(ChatbotMessageJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return ChatbotMessage.reconstitute(entity.getId(), entity.getSessionId(), entity.getQuestion(),
                entity.getAnswer(), entity.getCreatedAt());
    }
}
