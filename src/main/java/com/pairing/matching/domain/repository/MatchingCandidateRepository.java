package com.pairing.matching.domain.repository;

import com.pairing.matching.domain.model.MatchingCandidate;

import java.util.List;
import java.util.Optional;

public interface MatchingCandidateRepository {

    MatchingCandidate save(MatchingCandidate candidate);

    List<MatchingCandidate> saveAll(List<MatchingCandidate> candidates);

    Optional<MatchingCandidate> findById(Long id);

    /** 한 회차의 후보 전체를 순위(rankNo) 순으로. */
    List<MatchingCandidate> findByRoundIdOrderByRankNo(Long roundId);

    /** 화면에 노출된 것만(headcount 이내). */
    List<MatchingCandidate> findByRoundIdAndExposedTrueOrderByRankNo(Long roundId);

    /**
     * 같은 프로젝트 안에서 과거 회차 전체(포지션 불문)에 걸쳐 화면에 이미 노출된 프리랜서 ID 목록.
     * 재추천 시 제외 대상 판단에 쓴다(R02 예외조건 5: "동일한 프로젝트 안에서는 이미 추천된 프리랜서가
     * 재추천 결과에 다시 노출되지 않는다" — 포지션 단위가 아니라 프로젝트 단위임에 주의).
     */
    List<Long> findFreelancerIdsByProjectId(Long projectId);
}
