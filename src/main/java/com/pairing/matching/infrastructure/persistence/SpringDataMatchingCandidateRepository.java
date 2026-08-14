package com.pairing.matching.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpringDataMatchingCandidateRepository extends JpaRepository<MatchingCandidateJpaEntity, Long> {

    List<MatchingCandidateJpaEntity> findByRoundIdOrderByRankNoAsc(Long roundId);

    List<MatchingCandidateJpaEntity> findByRoundIdAndExposedTrueOrderByRankNoAsc(Long roundId);

    /**
     * 회차를 넘어 <b>포지션 전체</b>의 노출 후보. rankNo는 회차 안에서만 유효한 순위라 회차를 먼저
     * 정렬해야 한다. 최신 회차가 위로 온다 - 방금 재추천으로 받은 후보를 스크롤해서 찾게 하면 안 된다.
     */
    @Query("select c from MatchingCandidateJpaEntity c "
            + "join MatchingRoundJpaEntity r on r.id = c.roundId "
            + "where c.positionId = :positionId "
            + "and c.exposed = true "
            + "order by r.roundNo desc, c.rankNo asc")
    List<MatchingCandidateJpaEntity> findExposedByPositionId(@Param("positionId") Long positionId);

    @Query("select distinct c.freelancerId from MatchingCandidateJpaEntity c "
            + "join MatchingRoundJpaEntity r on r.id = c.roundId "
            + "where r.projectId = :projectId "
            + "and c.exposed = true")
    List<Long> findDistinctFreelancerIdByProjectId(@Param("projectId") Long projectId);
}
