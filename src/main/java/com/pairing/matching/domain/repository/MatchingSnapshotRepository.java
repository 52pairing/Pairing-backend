package com.pairing.matching.domain.repository;

import com.pairing.matching.domain.model.MatchingSnapshot;
import com.pairing.matching.domain.model.SnapshotType;

import java.util.Optional;

public interface MatchingSnapshotRepository {

    MatchingSnapshot save(MatchingSnapshot snapshot);

    /** 협상 도메인이 diff 낼 때 쓰는 조회. 같은 (position, type) 조합은 1건만 있다고 가정한다. */
    Optional<MatchingSnapshot> findByPositionIdAndSnapshotType(Long positionId, SnapshotType snapshotType);

    Optional<MatchingSnapshot> findByFreelancerIdAndPositionIdAndSnapshotType(Long freelancerId, Long positionId,
                                                                               SnapshotType snapshotType);
}
