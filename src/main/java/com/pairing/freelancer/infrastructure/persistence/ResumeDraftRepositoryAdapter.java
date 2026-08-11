package com.pairing.freelancer.infrastructure.persistence;

import com.pairing.freelancer.domain.model.ResumeDraft;
import com.pairing.freelancer.domain.repository.ResumeDraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ResumeDraftRepositoryAdapter implements ResumeDraftRepository {

    private final SpringDataResumeDraftRepository springDataRepository;

    @Override
    public ResumeDraft save(ResumeDraft draft) {
        ResumeDraftJpaEntity saved = springDataRepository.save(new ResumeDraftJpaEntity(
                draft.getId(), draft.getAccountId(), draft.getPayload(), draft.getUpdatedAt()));
        return toDomain(saved);
    }

    @Override
    public Optional<ResumeDraft> findByAccountId(Long accountId) {
        return springDataRepository.findByAccountId(accountId).map(this::toDomain);
    }

    @Override
    public void deleteByAccountId(Long accountId) {
        springDataRepository.deleteByAccountId(accountId);
    }

    private ResumeDraft toDomain(ResumeDraftJpaEntity entity) {
        return ResumeDraft.reconstitute(entity.getId(), entity.getAccountId(), entity.getPayload(),
                entity.getUpdatedAt());
    }
}
