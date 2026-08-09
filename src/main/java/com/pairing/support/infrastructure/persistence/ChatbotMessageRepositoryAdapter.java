package com.pairing.support.infrastructure.persistence;

import com.pairing.support.domain.model.ChatbotMessage;
import com.pairing.support.domain.repository.ChatbotMessageRepository;
import com.pairing.support.infrastructure.mapper.ChatbotMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChatbotMessageRepositoryAdapter implements ChatbotMessageRepository {

    private final SpringDataChatbotMessageRepository springDataRepository;
    private final ChatbotMessageMapper chatbotMessageMapper;

    @Override
    public ChatbotMessage save(ChatbotMessage message) {
        return chatbotMessageMapper.toDomain(springDataRepository.save(chatbotMessageMapper.toJpaEntity(message)));
    }

    @Override
    public List<ChatbotMessage> findBySessionId(Long sessionId) {
        return springDataRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(chatbotMessageMapper::toDomain)
                .toList();
    }
}
