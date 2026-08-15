package com.pairing.negotiation.infrastructure.persistence;

import com.pairing.negotiation.domain.model.NegotiationMessageType;
import com.pairing.negotiation.domain.model.SenderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SpringDataNegotiationMessageRepository extends JpaRepository<NegotiationMessageJpaEntity, Long> {

    List<NegotiationMessageJpaEntity> findByNegotiationIdOrderByRoundNoAscIdAsc(Long negotiationId);

    Optional<NegotiationMessageJpaEntity> findFirstByNegotiationIdAndConditionIdAndMessageTypeOrderByRoundNoDescIdDesc(
            Long negotiationId, Long conditionId, NegotiationMessageType messageType);

    /** 내 편(발신자) 제안을 뺀 최신 제안. 수락 대상은 상대가 낸 값이어야 하므로 그걸 고르는 데 쓴다. */
    Optional<NegotiationMessageJpaEntity>
    findFirstByNegotiationIdAndConditionIdAndMessageTypeAndSenderTypeNotInOrderByRoundNoDescIdDesc(
            Long negotiationId, Long conditionId, NegotiationMessageType messageType,
            Collection<SenderType> senderTypes);

    /** 체인 머리(가장 최근 로그). id 순으로 append 되므로 최대 id 가 머리다. */
    Optional<NegotiationMessageJpaEntity> findFirstByNegotiationIdOrderByIdDesc(Long negotiationId);

    /** 특정 라운드의 제안 개수(진행조회 "새 제안 개수"). */
    long countByNegotiationIdAndMessageTypeAndRoundNo(Long negotiationId, NegotiationMessageType messageType,
                                                      int roundNo);

    /** 협상의 최신 제안(조건 무관). */
    Optional<NegotiationMessageJpaEntity> findFirstByNegotiationIdAndMessageTypeOrderByRoundNoDescIdDesc(
            Long negotiationId, NegotiationMessageType messageType);

    /**
     * 여러 협상의 제안을 협상별 최신순으로 한 번에. 목록 배치용. 협상당 제안 수가 적어 페이지 하나면
     * 결과가 작다. 각 협상의 최신(첫 행)만 취하는 축약은 어댑터에서 한다.
     */
    List<NegotiationMessageJpaEntity>
    findByNegotiationIdInAndMessageTypeOrderByNegotiationIdAscRoundNoDescIdDesc(
            Collection<Long> negotiationIds, NegotiationMessageType messageType);

    /** 현재 라운드(negotiation.totalRound)에 해당 타입 메시지가 있는 협상 ID. 목록 '내 응답 필요' 배치 판정용. */
    @Query("SELECT DISTINCT m.negotiationId FROM NegotiationMessageJpaEntity m, NegotiationJpaEntity n "
            + "WHERE m.negotiationId = n.id AND m.negotiationId IN :ids "
            + "AND m.messageType = :type AND m.roundNo = n.totalRound")
    List<Long> findNegotiationIdsWithMessageInCurrentRound(
            @Param("ids") Collection<Long> ids, @Param("type") NegotiationMessageType type);

    /** 현재 라운드에 이 주체(sender)의 해당 타입 메시지가 있는 협상 ID. */
    @Query("SELECT DISTINCT m.negotiationId FROM NegotiationMessageJpaEntity m, NegotiationJpaEntity n "
            + "WHERE m.negotiationId = n.id AND m.negotiationId IN :ids "
            + "AND m.messageType = :type AND m.senderType = :sender AND m.roundNo = n.totalRound")
    List<Long> findNegotiationIdsWithSenderMessageInCurrentRound(
            @Param("ids") Collection<Long> ids, @Param("type") NegotiationMessageType type,
            @Param("sender") SenderType sender);

    /** 특정 라운드에 이 주체가 남긴 응답 개수. */
    long countByNegotiationIdAndSenderTypeAndRoundNoAndMessageType(
            Long negotiationId, SenderType senderType, int roundNo, NegotiationMessageType messageType);

    /** 협상의 전체 제안 수(아직 아무 것도 안 읽은 경우). */
    long countByNegotiationIdAndMessageType(Long negotiationId, NegotiationMessageType messageType);

    /** 특정 시각 이후 생성된 제안 수(마지막 읽음 이후 새 제안). */
    long countByNegotiationIdAndMessageTypeAndCreatedAtAfter(
            Long negotiationId, NegotiationMessageType messageType, LocalDateTime createdAt);
}
