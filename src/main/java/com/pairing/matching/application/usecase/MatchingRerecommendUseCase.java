package com.pairing.matching.application.usecase;

import com.pairing.matching.domain.model.RecommendationType;
import com.pairing.matching.presentation.api.response.CandidateListResponse;

/** 재추천 인바운드 포트. */
public interface MatchingRerecommendUseCase {

    /**
     * FREE는 발송한 요청이 전원 거절·만료됐을 때 프로젝트 전체 기준 1회, PAID는 최대 5회(1명당 10,000원)다.
     * quantity는 PAID일 때만 쓴다.
     */
    CandidateListResponse rerecommend(Long positionId, RecommendationType type, Integer quantity, Long accountId);
}
