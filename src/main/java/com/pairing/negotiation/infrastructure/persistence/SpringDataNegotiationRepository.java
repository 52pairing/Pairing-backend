package com.pairing.negotiation.infrastructure.persistence;

import com.pairing.negotiation.domain.model.NegotiationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpringDataNegotiationRepository extends JpaRepository<NegotiationJpaEntity, Long> {

    @EntityGraph(attributePaths = "conditions")
    Optional<NegotiationJpaEntity> findWithConditionsById(Long id);

    /** 매칭 요청(request_id, UNIQUE) 기준 조회. 진행조회 연동용(조건 미로드). */
    Optional<NegotiationJpaEntity> findByRequestId(Long requestId);

    /**
     * 내가 프리랜서인 협상(freelancer_profile.id 기준) 페이징. 목록은 조건을 로드하지 않는다
     * (요약 화면엔 조건이 필요 없고, 컬렉션 fetch 는 DB 페이징을 인메모리로 떨어뜨리므로).
     * 정렬은 Pageable 로 받는다. status 가 null 이면 전체.
     */
    @Query("SELECT n FROM NegotiationJpaEntity n "
            + "WHERE n.freelancerId = :freelancerProfileId AND (:status IS NULL OR n.status = :status)")
    Page<NegotiationJpaEntity> findByFreelancerId(@Param("freelancerProfileId") Long freelancerProfileId,
                                                  @Param("status") NegotiationStatus status, Pageable pageable);

    /** 특정 프로젝트의 협상(클라 협상 탭) 페이징. 소유 검증은 서비스 계층에서. */
    @Query("SELECT n FROM NegotiationJpaEntity n "
            + "WHERE n.projectId = :projectId AND (:status IS NULL OR n.status = :status)")
    Page<NegotiationJpaEntity> findByProjectId(@Param("projectId") Long projectId,
                                               @Param("status") NegotiationStatus status, Pageable pageable);

    /** 특정 프로젝트·상태 협상 전체(조건 자식까지 로드). 프로젝트 취소 시 진행 중 협상 일괄 결렬용. */
    @EntityGraph(attributePaths = "conditions")
    List<NegotiationJpaEntity> findByProjectIdAndStatus(Long projectId, NegotiationStatus status);

    /**
     * 내가 프리랜서인 협상 중 '내 응답 대기'인 건수. 헤더 배지용이라 목록을 다 읽지 않고 DB 에서 센다.
     *
     * <p>판정은 목록의 waitingForMe 와 같다: 진행 중 + 이번 라운드에 AI 제안이 있는데 내 응답이 아직 없음.
     */
    @Query("""
            SELECT COUNT(n) FROM NegotiationJpaEntity n
            WHERE n.freelancerId = :freelancerProfileId
              AND n.status = com.pairing.negotiation.domain.model.NegotiationStatus.IN_PROGRESS
              AND n.totalRound > 0
              AND EXISTS (SELECT 1 FROM NegotiationMessageJpaEntity p
                          WHERE p.negotiationId = n.id AND p.roundNo = n.totalRound
                            AND p.messageType = com.pairing.negotiation.domain.model.NegotiationMessageType.PROPOSAL)
              AND NOT EXISTS (SELECT 1 FROM NegotiationMessageJpaEntity r
                              WHERE r.negotiationId = n.id AND r.roundNo = n.totalRound
                                AND r.messageType = com.pairing.negotiation.domain.model.NegotiationMessageType.RESPONSE
                                AND r.senderType = com.pairing.negotiation.domain.model.SenderType.FREELANCER)
            """)
    long countWaitingForFreelancer(@Param("freelancerProfileId") Long freelancerProfileId);

    /**
     * 내가 클라인 협상 중 '내 응답 대기'인 건수. 협상은 clientProfileId 를 갖지 않아 프로젝트 ID 목록으로 받는다.
     * 목록이 비면 호출하지 않는다(빈 IN 절은 DB 마다 동작이 갈린다).
     */
    @Query("""
            SELECT COUNT(n) FROM NegotiationJpaEntity n
            WHERE n.projectId IN :projectIds
              AND n.status = com.pairing.negotiation.domain.model.NegotiationStatus.IN_PROGRESS
              AND n.totalRound > 0
              AND EXISTS (SELECT 1 FROM NegotiationMessageJpaEntity p
                          WHERE p.negotiationId = n.id AND p.roundNo = n.totalRound
                            AND p.messageType = com.pairing.negotiation.domain.model.NegotiationMessageType.PROPOSAL)
              AND NOT EXISTS (SELECT 1 FROM NegotiationMessageJpaEntity r
                              WHERE r.negotiationId = n.id AND r.roundNo = n.totalRound
                                AND r.messageType = com.pairing.negotiation.domain.model.NegotiationMessageType.RESPONSE
                                AND r.senderType = com.pairing.negotiation.domain.model.SenderType.CLIENT)
            """)
    long countWaitingForClient(@Param("projectIds") List<Long> projectIds);
}
