package com.pairing.matching.infrastructure.persistence;

import com.pairing.matching.domain.model.MatchingRoundStatus;
import com.pairing.matching.domain.model.RecommendationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataMatchingRoundRepository extends JpaRepository<MatchingRoundJpaEntity, Long> {

    Optional<MatchingRoundJpaEntity> findFirstByPositionIdOrderByRoundNoDesc(Long positionId);

    long countByPositionId(Long positionId);

    long countByProjectIdAndRoundTypeAndStatusNot(Long projectId, RecommendationType roundType,
                                                  MatchingRoundStatus excludedStatus);
}
