package com.pairing.matching.infrastructure.persistence;

import com.pairing.matching.domain.model.MatchingSnapshot;
import com.pairing.matching.domain.model.SnapshotType;
import com.pairing.matching.domain.repository.MatchingSnapshotRepository;
import com.pairing.matching.infrastructure.mapper.MatchingSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MatchingSnapshotRepositoryAdapter implements MatchingSnapshotRepository {

    private final SpringDataMatchingSnapshotRepository springDataRepository;
    private final MatchingSnapshotMapper matchingSnapshotMapper;

    @Override
    public MatchingSnapshot save(MatchingSnapshot snapshot) {
        MatchingSnapshotJpaEntity saved = springDataRepository.save(matchingSnapshotMapper.toJpaEntity(snapshot));
        return matchingSnapshotMapper.toDomain(saved);
    }

    @Override
    public Optional<MatchingSnapshot> findByPositionIdAndSnapshotType(Long positionId, SnapshotType snapshotType) {
        return springDataRepository.findByPositionIdAndSnapshotType(positionId, snapshotType)
                .map(matchingSnapshotMapper::toDomain);
    }

    @Override
    public Optional<MatchingSnapshot> findByFreelancerIdAndPositionIdAndSnapshotType(
            Long freelancerId, Long positionId, SnapshotType snapshotType) {
        return springDataRepository.findByFreelancerIdAndPositionIdAndSnapshotType(freelancerId, positionId,
                        snapshotType)
                .map(matchingSnapshotMapper::toDomain);
    }
}
