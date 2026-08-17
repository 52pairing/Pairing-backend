package com.pairing.matching.infrastructure.persistence;

import com.pairing.matching.domain.model.SnapshotType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataMatchingSnapshotRepository extends JpaRepository<MatchingSnapshotJpaEntity, Long> {

    Optional<MatchingSnapshotJpaEntity> findByPositionIdAndSnapshotType(Long positionId, SnapshotType snapshotType);

    Optional<MatchingSnapshotJpaEntity> findByFreelancerIdAndPositionIdAndSnapshotType(
            Long freelancerId, Long positionId, SnapshotType snapshotType);

    List<MatchingSnapshotJpaEntity> findAllBySnapshotType(SnapshotType snapshotType);

    List<MatchingSnapshotJpaEntity> findAllByPositionIdAndSnapshotType(Long positionId, SnapshotType snapshotType);

    List<MatchingSnapshotJpaEntity> findAllByPositionIdInAndSnapshotType(List<Long> positionIds,
                                                                          SnapshotType snapshotType);
}
