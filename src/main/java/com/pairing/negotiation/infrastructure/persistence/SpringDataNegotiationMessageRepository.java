package com.pairing.negotiation.infrastructure.persistence;

import com.pairing.negotiation.domain.model.NegotiationMessageType;
import com.pairing.negotiation.domain.model.SenderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataNegotiationMessageRepository extends JpaRepository<NegotiationMessageJpaEntity, Long> {

    List<NegotiationMessageJpaEntity> findByNegotiationIdOrderByRoundNoAscIdAsc(Long negotiationId);

    Optional<NegotiationMessageJpaEntity> findFirstByNegotiationIdAndConditionIdAndMessageTypeOrderByRoundNoDescIdDesc(
            Long negotiationId, Long conditionId, NegotiationMessageType messageType);

    /** 체인 머리(가장 최근 로그). id 순으로 append 되므로 최대 id 가 머리다. */
    Optional<NegotiationMessageJpaEntity> findFirstByNegotiationIdOrderByIdDesc(Long negotiationId);

    /** 특정 라운드의 제안 개수(진행조회 "새 제안 개수"). */
    long countByNegotiationIdAndMessageTypeAndRoundNo(Long negotiationId, NegotiationMessageType messageType,
                                                      int roundNo);

    /** 협상의 최신 제안(조건 무관). */
    Optional<NegotiationMessageJpaEntity> findFirstByNegotiationIdAndMessageTypeOrderByRoundNoDescIdDesc(
            Long negotiationId, NegotiationMessageType messageType);

    /** 특정 라운드에 이 주체가 남긴 응답 개수. */
    long countByNegotiationIdAndSenderTypeAndRoundNoAndMessageType(
            Long negotiationId, SenderType senderType, int roundNo, NegotiationMessageType messageType);
}
