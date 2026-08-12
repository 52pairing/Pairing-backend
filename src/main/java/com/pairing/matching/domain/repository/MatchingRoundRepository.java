package com.pairing.matching.domain.repository;

import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.RecommendationType;

import java.util.List;
import java.util.Optional;

public interface MatchingRoundRepository {

    MatchingRound save(MatchingRound round);

    Optional<MatchingRound> findById(Long id);

    /** 포지션의 가장 최근 회차. "추천 후보 조회"는 항상 이 회차 기준이다. */
    Optional<MatchingRound> findLatestByPositionId(Long positionId);

    /** 다음 회차 번호를 정하기 위한 현재까지의 회차 수. */
    long countByPositionId(Long positionId);

    /** 관리자 재색인 대상. 스냅샷이 아니라 실제 모집 라운드가 생긴 포지션별 최신 라운드를 기준으로 삼는다. */
    List<MatchingRound> findLatestRoundsByDistinctPosition();

    /**
     * 프로젝트 전체(포지션 불문)에서 지금까지 <b>실제로 쓴</b> 특정 종류의 회차 수.
     * 무료/유료 재추천 한도는 포지션이 아니라 프로젝트 전체 기준이다(P40/P41).
     *
     * <p>{@code FAILED} 회차는 세지 않는다. 재추천은 회차를 먼저 만들고 AI 호출은 비동기로 하는데,
     * AI 서버가 죽어 후보를 한 명도 못 받은 회차까지 한도로 치면 서버 장애 때문에 무료 1회가
     * 영영 사라진다.
     */
    long countByProjectIdAndRoundType(Long projectId, RecommendationType roundType);
}
