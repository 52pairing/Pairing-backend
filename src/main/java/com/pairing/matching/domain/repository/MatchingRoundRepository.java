package com.pairing.matching.domain.repository;

import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.RecommendationType;

import java.util.Optional;

public interface MatchingRoundRepository {

    MatchingRound save(MatchingRound round);

    Optional<MatchingRound> findById(Long id);

    /** 포지션의 가장 최근 회차. "추천 후보 조회"는 항상 이 회차 기준이다. */
    Optional<MatchingRound> findLatestByPositionId(Long positionId);

    /** 다음 회차 번호를 정하기 위한 현재까지의 회차 수. */
    long countByPositionId(Long positionId);

    /**
     * 프로젝트 전체(포지션 불문)에서 지금까지 만들어진 특정 종류의 회차 수.
     * 무료/유료 재추천 한도는 포지션이 아니라 프로젝트 전체 기준이다(P40/P41).
     */
    long countByProjectIdAndRoundType(Long projectId, RecommendationType roundType);
}
