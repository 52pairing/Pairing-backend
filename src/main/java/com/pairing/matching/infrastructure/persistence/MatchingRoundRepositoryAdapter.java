package com.pairing.matching.infrastructure.persistence;

import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.RecommendationType;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import com.pairing.matching.infrastructure.mapper.MatchingRoundMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MatchingRoundRepositoryAdapter implements MatchingRoundRepository {

    private final SpringDataMatchingRoundRepository springDataRepository;
    private final MatchingRoundMapper matchingRoundMapper;

    @Override
    public MatchingRound save(MatchingRound round) {
        MatchingRoundJpaEntity saved = springDataRepository.save(matchingRoundMapper.toJpaEntity(round));
        return matchingRoundMapper.toDomain(saved);
    }

    @Override
    public Optional<MatchingRound> findById(Long id) {
        return springDataRepository.findById(id).map(matchingRoundMapper::toDomain);
    }

    @Override
    public Optional<MatchingRound> findLatestByPositionId(Long positionId) {
        return springDataRepository.findFirstByPositionIdOrderByRoundNoDesc(positionId)
                .map(matchingRoundMapper::toDomain);
    }

    @Override
    public long countByPositionId(Long positionId) {
        return springDataRepository.countByPositionId(positionId);
    }

    @Override
    public long countByProjectIdAndRoundType(Long projectId, RecommendationType roundType) {
        return springDataRepository.countByProjectIdAndRoundType(projectId, roundType);
    }
}
