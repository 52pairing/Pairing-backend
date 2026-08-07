package com.pairing.negotiation.infrastructure.persistence;

import com.pairing.negotiation.domain.model.NegotiationMessage;
import com.pairing.negotiation.domain.model.NegotiationMessageType;
import com.pairing.negotiation.domain.repository.NegotiationMessageRepository;
import com.pairing.negotiation.infrastructure.mapper.NegotiationMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NegotiationMessageRepositoryAdapter implements NegotiationMessageRepository {

    private final SpringDataNegotiationMessageRepository springDataRepository;
    private final NegotiationMessageMapper mapper;

    @Override
    public NegotiationMessage save(NegotiationMessage message) {
        return mapper.toDomain(springDataRepository.save(mapper.toJpaEntity(message)));
    }

    @Override
    public List<NegotiationMessage> saveAll(List<NegotiationMessage> messages) {
        return springDataRepository.saveAll(messages.stream().map(mapper::toJpaEntity).toList())
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<NegotiationMessage> findByNegotiationId(Long negotiationId) {
        return springDataRepository.findByNegotiationIdOrderByRoundNoAscIdAsc(negotiationId).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public Optional<NegotiationMessage> findLatestProposal(Long negotiationId, Long conditionId) {
        return springDataRepository
                .findFirstByNegotiationIdAndConditionIdAndMessageTypeOrderByRoundNoDescIdDesc(
                        negotiationId, conditionId, NegotiationMessageType.PROPOSAL)
                .map(mapper::toDomain);
    }
}
