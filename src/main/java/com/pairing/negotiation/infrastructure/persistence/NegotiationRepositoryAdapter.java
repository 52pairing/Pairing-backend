package com.pairing.negotiation.infrastructure.persistence;

import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import com.pairing.negotiation.infrastructure.mapper.NegotiationMapper;
import lombok.RequiredArgsConstructor;
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
    public List<Negotiation> findByFreelancerId(Long freelancerProfileId) {
        return springDataRepository.findByFreelancerId(freelancerProfileId).stream()
                .map(negotiationMapper::toDomain)
                .toList();
    }

    @Override
    public List<Negotiation> findByProjectId(Long projectId) {
        return springDataRepository.findByProjectId(projectId).stream()
                .map(negotiationMapper::toDomain)
                .toList();
    }
}
