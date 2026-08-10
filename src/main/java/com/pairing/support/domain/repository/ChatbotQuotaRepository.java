package com.pairing.support.domain.repository;

import com.pairing.support.domain.model.ChatbotQuota;

import java.time.LocalDate;
import java.util.Optional;

public interface ChatbotQuotaRepository {

    ChatbotQuota save(ChatbotQuota quota);

    Optional<ChatbotQuota> findByAccountIdAndQuotaDate(Long accountId, LocalDate quotaDate);
}
