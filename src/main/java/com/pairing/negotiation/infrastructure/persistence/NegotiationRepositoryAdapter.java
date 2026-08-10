package com.pairing.negotiation.infrastructure.persistence;

import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import com.pairing.negotiation.infrastructure.mapper.NegotiationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NegotiationRepositoryAdapter implements NegotiationRepository {

    private final SpringDataNegotiationRepository springDataRepository;
    private final NegotiationMapper negotiationMapper;

    @Override
    public Negotiation save(Negotiation negotiation) {
        NegotiationJpaEntity saved = springDataRepository.save(negotiationMapper.toJpaEntity(negotiation));
        return negotiationMapper.toDomain(saved);
    }

    @Override
    public Optional<Negotiation> findById(Long id) {
        return springDataRepository.findWithConditionsById(id)
                .map(negotiationMapper::toDomain);
    }

    @Override
    public Optional<Negotiation> findByRequestId(Long requestId) {
        // 요약 매핑(조건 미로드) — 진행조회는 라운드·상태만 필요하다.
        return springDataRepository.findByRequestId(requestId)
                .map(negotiationMapper::toDomainSummary);
    }

    @Override
    public Page<Negotiation> findByFreelancerId(Long freelancerProfileId, NegotiationStatus status,
                                                Pageable pageable) {
        return springDataRepository.findByFreelancerId(freelancerProfileId, status, pageable)
                .map(negotiationMapper::toDomainSummary);
    }

    @Override
    public Page<Negotiation> findByProjectId(Long projectId, NegotiationStatus status, Pageable pageable) {
        return springDataRepository.findByProjectId(projectId, status, pageable)
                .map(negotiationMapper::toDomainSummary);
    }

    @Override
    public long countWaitingForFreelancer(Long freelancerProfileId) {
        return springDataRepository.countWaitingForFreelancer(freelancerProfileId);
    }

    @Override
    public long countWaitingForClient(List<Long> projectIds) {
        // 빈 IN 절은 DB 마다 동작이 갈린다. 프로젝트가 없으면 셀 협상도 없으므로 질의하지 않는다.
        if (projectIds == null || projectIds.isEmpty()) {
            return 0L;
        }
        return springDataRepository.countWaitingForClient(projectIds);
    }
}
