package com.pairing.negotiation.infrastructure.persistence;

import com.pairing.negotiation.domain.model.NegotiationMessage;
import com.pairing.negotiation.domain.model.NegotiationMessageType;
import com.pairing.negotiation.domain.model.SenderType;
import com.pairing.negotiation.domain.repository.NegotiationMessageRepository;
import com.pairing.negotiation.infrastructure.mapper.NegotiationMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
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

    @Override
    public Optional<NegotiationMessage> findLatestProposalExcluding(Long negotiationId, Long conditionId,
                                                                   Collection<SenderType> excluded) {
        return springDataRepository
                .findFirstByNegotiationIdAndConditionIdAndMessageTypeAndSenderTypeNotInOrderByRoundNoDescIdDesc(
                        negotiationId, conditionId, NegotiationMessageType.PROPOSAL, excluded)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<String> findLatestHash(Long negotiationId) {
        return springDataRepository.findFirstByNegotiationIdOrderByIdDesc(negotiationId)
                .map(NegotiationMessageJpaEntity::getContentHash);
    }

    @Override
    public int countProposalsInRound(Long negotiationId, int roundNo) {
        return (int) springDataRepository.countByNegotiationIdAndMessageTypeAndRoundNo(
                negotiationId, NegotiationMessageType.PROPOSAL, roundNo);
    }

    @Override
    public Optional<NegotiationMessage> findLatestProposal(Long negotiationId) {
        return springDataRepository
                .findFirstByNegotiationIdAndMessageTypeOrderByRoundNoDescIdDesc(
                        negotiationId, NegotiationMessageType.PROPOSAL)
                .map(mapper::toDomain);
    }

    @Override
    public int countResponsesInRound(Long negotiationId, SenderType senderType, int roundNo) {
        return (int) springDataRepository.countByNegotiationIdAndSenderTypeAndRoundNoAndMessageType(
                negotiationId, senderType, roundNo, NegotiationMessageType.RESPONSE);
    }

    @Override
    public int countUnreadProposals(Long negotiationId, LocalDateTime after) {
        if (after == null) {
            return (int) springDataRepository.countByNegotiationIdAndMessageType(
                    negotiationId, NegotiationMessageType.PROPOSAL);
        }
        return (int) springDataRepository.countByNegotiationIdAndMessageTypeAndCreatedAtAfter(
                negotiationId, NegotiationMessageType.PROPOSAL, after);
    }
}
