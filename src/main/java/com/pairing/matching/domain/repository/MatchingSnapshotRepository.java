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

    /**
     * 한 포지션의 스냅샷 전부. 후보 목록이 프리랜서 스냅샷을 <b>한 번에</b> 읽는 데 쓴다.
     *
     * <p>{@link #findByFreelancerIdAndPositionIdAndSnapshotType}을 후보마다 부르면 카드 1장당 쿼리가
     * 하나씩 붙는다. FREELANCER 타입은 (freelancer, position) 조합마다 1건이라 포지션으로 묶어 읽고
     * 메모리에서 프리랜서별로 나눈다.
     */
    List<MatchingSnapshot> findAllByPositionIdAndSnapshotType(Long positionId, SnapshotType snapshotType);

    /**
     * 여러 포지션의 한 타입 스냅샷을 한 번에 읽는다. 매칭 요청 목록이 행마다 PROJECT/POSITION을
     * 각각 조회하던 것을 페이지당 타입별 1번으로 줄이는 데 쓴다.
     */
    List<MatchingSnapshot> findAllByPositionIdInAndSnapshotType(List<Long> positionIds, SnapshotType snapshotType);
}
