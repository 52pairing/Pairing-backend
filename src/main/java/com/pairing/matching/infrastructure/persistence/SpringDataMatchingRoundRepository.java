package com.pairing.matching.infrastructure.persistence;

import com.pairing.matching.domain.model.MatchingRoundStatus;
import com.pairing.matching.domain.model.RecommendationType;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    List<MatchingRoundJpaEntity> findByStatusAndCreatedAtBefore(MatchingRoundStatus status,
                                                                LocalDateTime threshold);

    /**
     * 회차 행을 <b>잠그고</b> 읽는다({@code SELECT ... FOR UPDATE}).
     *
     * <p>후보 채우기를 두 곳에서 동시에 시작하는 것을 막는 데 쓴다. 상태만 확인하면 두 트랜잭션이
     * 나란히 {@code RUNNING}을 읽고 둘 다 진행한다 — 그러면 Gemini를 두 번 부르고 후보가 중복 저장된다.
     *
     * <p>읽기(후보 목록 조회)는 이 잠금에 막히지 않는다. PostgreSQL은 MVCC라 {@code FOR UPDATE}가
     * 일반 SELECT를 세우지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MatchingRoundJpaEntity r where r.id = :id")
    Optional<MatchingRoundJpaEntity> findByIdForUpdate(@Param("id") Long id);
}
