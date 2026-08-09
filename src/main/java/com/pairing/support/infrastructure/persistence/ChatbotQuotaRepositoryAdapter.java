package com.pairing.support.infrastructure.persistence;

import com.pairing.support.domain.model.ChatbotQuota;
import com.pairing.support.domain.repository.ChatbotQuotaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChatbotQuotaRepositoryAdapter implements ChatbotQuotaRepository {

    private final SpringDataChatbotQuotaRepository springDataRepository;

    @Override
    public ChatbotQuota save(ChatbotQuota quota) {
        ChatbotQuotaJpaEntity entity = new ChatbotQuotaJpaEntity(quota.getId(), quota.getAccountId(),
                quota.getQuotaDate(), quota.getUsedCount());
        ChatbotQuotaJpaEntity saved = springDataRepository.save(entity);
        return ChatbotQuota.reconstitute(saved.getId(), saved.getAccountId(), saved.getQuotaDate(),
                saved.getUsedCount());
    }

    @Override
    public Optional<ChatbotQuota> findByAccountIdAndQuotaDate(Long accountId, LocalDate quotaDate) {
        return springDataRepository.findByAccountIdAndQuotaDate(accountId, quotaDate)
                .map(entity -> ChatbotQuota.reconstitute(entity.getId(), entity.getAccountId(),
                        entity.getQuotaDate(), entity.getUsedCount()));
    }
}
