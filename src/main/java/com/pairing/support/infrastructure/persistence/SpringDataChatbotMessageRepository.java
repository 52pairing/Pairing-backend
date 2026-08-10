package com.pairing.support.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataChatbotMessageRepository extends JpaRepository<ChatbotMessageJpaEntity, Long> {

    List<ChatbotMessageJpaEntity> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
