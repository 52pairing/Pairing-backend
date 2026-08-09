package com.pairing.matching.application.port.out;

import com.pairing.matching.application.result.CandidatePool;
import com.pairing.matching.application.result.MatchingRecommendation;

import java.util.List;

/**
 * AI 서버(Pairing-python) 호출 포트.
 *
 * <p>임베딩 계산 자체(1차 추림)와 LLM 최종 선정 모두 AI 서버가 수행한다. 여기는 호출만 한다.
 */
public interface MatchingPort {

    /** Stage C~D: 포지션의 1차 후보 풀을 조회한다. limit = 모집 인원 x pool_multiplier. */
    CandidatePool searchCandidates(Long positionId, int limit);

    /**
     * Stage B~E: 하드필터(AI매칭 동의+직군/직무 일치, AI 서버가 벡터 검색 전에 적용)로 좁힌 후보 풀을
     * LLM에 넘겨 최종 순위·근거를 받는다. 일정/근무조건/단가는 필터 대상이 아니다 — LLM이 감점+사유로
     * 반영한다(`.ai/STATE.md` "Stage B 조건필터 폐기" 참고).
     *
     * @param excludedFreelancerIds 같은 프로젝트에서 이미 후보로 노출됐던 프리랜서(R02 예외조건 5).
     *                              AI 서버가 벡터 검색 전에 제외해서 풀 크기가 줄어들지 않는다.
     */
    MatchingRecommendation recommend(Long positionId, int recruitCount, int poolMultiplier,
                                     List<Long> excludedFreelancerIds);

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
