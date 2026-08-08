package com.pairing.matching.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpringDataMatchingCandidateRepository extends JpaRepository<MatchingCandidateJpaEntity, Long> {

    List<MatchingCandidateJpaEntity> findByRoundIdOrderByRankNoAsc(Long roundId);

    List<MatchingCandidateJpaEntity> findByRoundIdAndExposedTrueOrderByRankNoAsc(Long roundId);

    @Query("select distinct c.freelancerId from MatchingCandidateJpaEntity c "
            + "join MatchingRoundJpaEntity r on r.id = c.roundId "
            + "where r.projectId = :projectId")
    List<Long> findDistinctFreelancerIdByProjectId(@Param("projectId") Long projectId);
}
