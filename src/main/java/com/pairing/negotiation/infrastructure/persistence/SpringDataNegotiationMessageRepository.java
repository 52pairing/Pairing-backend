package com.pairing.negotiation.infrastructure.persistence;

import com.pairing.negotiation.domain.model.NegotiationMessageType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataNegotiationMessageRepository extends JpaRepository<NegotiationMessageJpaEntity, Long> {

    List<NegotiationMessageJpaEntity> findByNegotiationIdOrderByRoundNoAscIdAsc(Long negotiationId);

    Optional<NegotiationMessageJpaEntity> findFirstByNegotiationIdAndConditionIdAndMessageTypeOrderByRoundNoDescIdDesc(
            Long negotiationId, Long conditionId, NegotiationMessageType messageType);
}
