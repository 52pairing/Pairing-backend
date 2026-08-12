package com.pairing.matching.infrastructure.persistence;

import com.pairing.matching.domain.model.MatchingRoundStatus;
import com.pairing.matching.domain.model.RecommendationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SpringDataMatchingRoundRepository extends JpaRepository<MatchingRoundJpaEntity, Long> {

    Optional<MatchingRoundJpaEntity> findFirstByPositionIdOrderByRoundNoDesc(Long positionId);

    long countByPositionId(Long positionId);

    @Query("""
            select r
            from MatchingRoundJpaEntity r
            where r.id in (
                select max(latest.id)
                from MatchingRoundJpaEntity latest
                group by latest.positionId
            )
            """)
    List<MatchingRoundJpaEntity> findLatestByDistinctPosition();

    long countByProjectIdAndRoundTypeAndStatusNot(Long projectId, RecommendationType roundType,
                                                  MatchingRoundStatus excludedStatus);
}
