package com.pairing.support.infrastructure.mapper;

import com.pairing.support.domain.model.ChatbotIntent;
import com.pairing.support.domain.model.ChatbotMessage;
import com.pairing.support.infrastructure.persistence.ChatbotMessageJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * {@code intent} 는 도메인에서 enum, 테이블에서는 문자열이라 양방향 모두 직접 변환한다.
 *
 * <p>읽을 때 {@link ChatbotIntent#from} 을 거치므로, 목록에서 사라진 화면 코드가 남아 있어도
 * 버튼만 빠지고 대화는 그대로 나온다.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ChatbotMessageMapper {

    default ChatbotMessageJpaEntity toJpaEntity(ChatbotMessage message) {
        if (message == null) {
            return null;
        }
        return new ChatbotMessageJpaEntity(message.getId(), message.getSessionId(), message.getQuestion(),
                message.getAnswer(), message.getIntent() == null ? null : message.getIntent().name(),
                message.getCreatedAt());
    }

    default ChatbotMessage toDomain(ChatbotMessageJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return ChatbotMessage.reconstitute(entity.getId(), entity.getSessionId(), entity.getQuestion(),
                entity.getAnswer(), ChatbotIntent.from(entity.getIntent()), entity.getCreatedAt());
    }
}
