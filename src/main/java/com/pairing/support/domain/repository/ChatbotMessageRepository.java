package com.pairing.support.domain.repository;

import com.pairing.support.domain.model.ChatbotMessage;

import java.util.List;

public interface ChatbotMessageRepository {

    ChatbotMessage save(ChatbotMessage message);

    /** 시간순(오래된 것부터). */
    List<ChatbotMessage> findBySessionId(Long sessionId);
}
