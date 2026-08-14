package com.pairing.matching.domain.repository;

import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.RecommendationType;

import java.time.LocalDateTime;
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

    /**
     * {@code threshold} 이전에 만들어졌는데 아직 {@code RUNNING}인 회차.
     *
     * <p>AI 호출 중에 컨테이너가 교체되면 그 스레드는 아무 흔적 없이 사라지고 회차가 영원히
     * {@code RUNNING}으로 남는다. 화면은 "추천 준비중"에서 멈춘다. 실제로 하루에 태스크 정의가
     * 네 번 바뀐 날이 있었다(2026-08-13).
     *
     * <p><b>이렇게 남은 회차에는 후보가 없다.</b> 후보 저장과 회차 완료가 한 트랜잭션이라
     * ({@code MatchingRoundFiller.fill}) 중간에 죽으면 통째로 롤백된다. 그래서 다시 채워도
     * 중복이 생기지 않는다.
     */
    List<MatchingRound> findStaleRunning(LocalDateTime threshold);
}
