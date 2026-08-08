package com.pairing.matching.application.port.out;

import com.pairing.matching.application.result.CandidatePool;
import com.pairing.matching.application.result.MatchingRecommendation;

/**
 * AI 서버(Pairing-python) 호출 포트.
 *
 * <p>임베딩 계산 자체(1차 추림)와 LLM 최종 선정 모두 AI 서버가 수행한다. 여기는 호출만 한다.
 * 임베딩 upsert(PUT /embeddings/*)는 프리랜서/프로젝트 저장 시점에 그 도메인에서 별도로 호출한다
 * (여기서는 다루지 않음 — 3일차 작업).
 */
public interface MatchingPort {

    /** Stage C~D: 포지션의 1차 후보 풀을 조회한다. limit = 모집 인원 x pool_multiplier. */
    CandidatePool searchCandidates(Long positionId, int limit);

    /** Stage E: 1차 후보 풀을 LLM에 넘겨 최종 순위·근거를 받는다. */
    MatchingRecommendation recommend(Long positionId, int recruitCount, int poolMultiplier);
}
