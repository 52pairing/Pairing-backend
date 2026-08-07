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

    /** 내가 프리랜서인 협상(freelancer_profile.id 기준). 최신순. */
    @EntityGraph(attributePaths = "conditions")
    @Query("SELECT n FROM NegotiationJpaEntity n WHERE n.freelancerId = :freelancerProfileId "
            + "ORDER BY n.startedAt DESC")
    List<NegotiationJpaEntity> findByFreelancerId(@Param("freelancerProfileId") Long freelancerProfileId);

    /** 특정 프로젝트의 협상(클라 협상 탭). 소유 검증은 서비스 계층에서. 최신순. */
    @EntityGraph(attributePaths = "conditions")
    @Query("SELECT n FROM NegotiationJpaEntity n WHERE n.projectId = :projectId ORDER BY n.startedAt DESC")
    List<NegotiationJpaEntity> findByProjectId(@Param("projectId") Long projectId);
}
