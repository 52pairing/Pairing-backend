package com.pairing.support.domain.repository;

import com.pairing.support.domain.model.ChatbotSession;

import java.util.Optional;

public interface ChatbotSessionRepository {

    ChatbotSession save(ChatbotSession session);

    Optional<ChatbotSession> findById(Long id);
}
