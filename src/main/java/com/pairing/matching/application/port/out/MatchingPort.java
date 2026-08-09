package com.pairing.matching.application.port.out;

import com.pairing.matching.application.result.CandidatePool;
import com.pairing.matching.application.result.MatchingRecommendation;

/**
 * AI 서버(Pairing-python) 호출 포트.
 *
 * <p>임베딩 계산 자체(1차 추림)와 LLM 최종 선정 모두 AI 서버가 수행한다. 여기는 호출만 한다.
 */
public interface MatchingPort {

    /** Stage C~D: 포지션의 1차 후보 풀을 조회한다. limit = 모집 인원 x pool_multiplier. */
    CandidatePool searchCandidates(Long positionId, int limit);

    /** Stage E: 1차 후보 풀을 LLM에 넘겨 최종 순위·근거를 받는다. */
    MatchingRecommendation recommend(Long positionId, int recruitCount, int poolMultiplier);

    /**
     * 포지션 임베딩을 upsert한다(PUT /embeddings/positions). 착수금 결제 완료로 모집이 시작될 때
     * 딱 1번 호출한다(프로젝트는 등록 후 수정 불가능이라 재계산 불필요).
     */
    void upsertPositionEmbedding(Long positionId, String text);

    /**
     * 프리랜서 임베딩을 upsert한다(PUT /embeddings/freelancers). 이력서 저장(자기소개+경력사항이
     * 바뀔 때)마다 호출한다 — {@code matching.application.service.ResumeUpdatedEventListener} 참고.
     */
    void upsertFreelancerEmbedding(Long freelancerId, String text);
}
