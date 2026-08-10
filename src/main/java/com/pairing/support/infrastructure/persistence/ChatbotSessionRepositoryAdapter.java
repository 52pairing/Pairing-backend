package com.pairing.support.infrastructure.persistence;

import com.pairing.support.domain.model.ChatbotSession;
import com.pairing.support.domain.repository.ChatbotSessionRepository;
import com.pairing.support.infrastructure.mapper.ChatbotSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChatbotSessionRepositoryAdapter implements ChatbotSessionRepository {

    private final SpringDataChatbotSessionRepository springDataRepository;
    private final ChatbotSessionMapper chatbotSessionMapper;

    @Override
    public ChatbotSession save(ChatbotSession session) {
        return chatbotSessionMapper.toDomain(springDataRepository.save(chatbotSessionMapper.toJpaEntity(session)));
    }

    @Override
    public Optional<ChatbotSession> findById(Long id) {
        return springDataRepository.findById(id).map(chatbotSessionMapper::toDomain);
    }
}
