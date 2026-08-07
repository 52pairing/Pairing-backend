package com.pairing.negotiation.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpringDataNegotiationRepository extends JpaRepository<NegotiationJpaEntity, Long> {

    @EntityGraph(attributePaths = "conditions")
    Optional<NegotiationJpaEntity> findWithConditionsById(Long id);

    /**
     * 내 협상 목록. projectId 가 있으면 해당 프로젝트 협상(클라 협상 탭), 없으면 내가 프리랜서인 협상.
     * 클라이언트의 프로젝트 소유 검증은 서비스 계층에서 수행한다.
     */
    @EntityGraph(attributePaths = "conditions")
    @Query("SELECT n FROM NegotiationJpaEntity n "
            + "WHERE (:projectId IS NULL AND n.freelancerId = :accountId) "
            + "   OR (:projectId IS NOT NULL AND n.projectId = :projectId) "
            + "ORDER BY n.startedAt DESC")
    List<NegotiationJpaEntity> findMine(@Param("accountId") Long accountId, @Param("projectId") Long projectId);
}
