package com.pairing.support.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataChatbotSessionRepository extends JpaRepository<ChatbotSessionJpaEntity, Long> {
}
