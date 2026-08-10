package com.pairing.support.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface SpringDataChatbotQuotaRepository extends JpaRepository<ChatbotQuotaJpaEntity, Long> {

    Optional<ChatbotQuotaJpaEntity> findByAccountIdAndQuotaDate(Long accountId, LocalDate quotaDate);
}
