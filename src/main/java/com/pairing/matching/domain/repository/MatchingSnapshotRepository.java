package com.pairing.matching.domain.repository;

import com.pairing.matching.domain.model.MatchingSnapshot;
import com.pairing.matching.domain.model.SnapshotType;

import java.util.List;
import java.util.Optional;

public interface MatchingSnapshotRepository {

    MatchingSnapshot save(MatchingSnapshot snapshot);

    /** 협상 도메인이 diff 낼 때 쓰는 조회. 같은 (position, type) 조합은 1건만 있다고 가정한다. */
    Optional<MatchingSnapshot> findByPositionIdAndSnapshotType(Long positionId, SnapshotType snapshotType);

    Optional<MatchingSnapshot> findByFreelancerIdAndPositionIdAndSnapshotType(Long freelancerId, Long positionId,
                                                                               SnapshotType snapshotType);

    /**
     * 주어진 타입의 스냅샷 전부. POSITION 타입은 "모집 시작"(임베딩 생성) 시점에만 만들어지므로,
     * 임베딩 일괄 재색인 대상 포지션을 고르는 데 쓴다.
     */
    List<MatchingSnapshot> findAllBySnapshotType(SnapshotType snapshotType);
}
