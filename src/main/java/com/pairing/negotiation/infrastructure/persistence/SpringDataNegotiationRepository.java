package com.pairing.negotiation.infrastructure.persistence;

import com.pairing.negotiation.domain.model.NegotiationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
