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
        // saveAndFlush 로 즉시 flush 해야 유니크 제약 위반이 여기서 바로 예외로 올라온다.
        // save() 만 쓰면 Hibernate가 flush 를 트랜잭션 커밋 시점까지 미뤄서, 호출부의 try-catch 로는 못 잡는다.
        ChatbotQuotaJpaEntity saved = springDataRepository.saveAndFlush(entity);
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
