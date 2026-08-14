package com.pairing.matching.infrastructure.persistence;

import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.MatchingRoundStatus;
import com.pairing.matching.domain.model.RecommendationType;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import com.pairing.matching.infrastructure.mapper.MatchingRoundMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
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
    public List<MatchingRound> findLatestRoundsByDistinctPosition() {
        return springDataRepository.findLatestByDistinctPosition().stream()
                .map(matchingRoundMapper::toDomain)
                .toList();
    }

    @Override
    public long countByProjectIdAndRoundType(Long projectId, RecommendationType roundType) {
        // FAILED 회차는 후보를 한 명도 못 만든 회차라 한도를 쓴 걸로 치지 않는다(포트 주석 참고).
        return springDataRepository.countByProjectIdAndRoundTypeAndStatusNot(projectId, roundType,
                MatchingRoundStatus.FAILED);
    }

    @Override
    public Optional<MatchingRound> findByIdForUpdate(Long id) {
        return springDataRepository.findByIdForUpdate(id).map(matchingRoundMapper::toDomain);
    }

    @Override
    public List<MatchingRound> findStaleRunning(LocalDateTime threshold) {
        return springDataRepository.findByStatusAndCreatedAtBefore(MatchingRoundStatus.RUNNING, threshold).stream()
                .map(matchingRoundMapper::toDomain)
                .toList();
    }
}
