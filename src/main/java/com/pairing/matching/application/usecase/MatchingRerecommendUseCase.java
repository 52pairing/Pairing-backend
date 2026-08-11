package com.pairing.matching.application.usecase;

import com.pairing.matching.domain.model.RecommendationType;

/** 재추천 인바운드 포트. */
public interface MatchingRerecommendUseCase {

    /**
     * FREE는 발송한 요청이 전원 거절·만료됐을 때 프로젝트 전체 기준 1회, PAID는 최대 5회(1명당 10,000원)다.
     * quantity는 PAID일 때만 쓴다.
     *
     * <p><b>후보 목록을 돌려주지 않는다.</b> 검증과 회차 생성까지만 하고 바로 반환하며, 실제 후보는
     * AI 호출이 끝난 뒤 비동기로 채워진다(수 초~수십 초). 완료되면 클라이언트에게
     * {@code MATCHING_RECOMMENDED} 알림이 가고, 그때 후보 목록을 다시 조회하면 된다.
     * 한도 초과·모집 종료 같은 검증 실패는 이 호출에서 즉시 예외로 던진다.
     */
    void rerecommend(Long positionId, RecommendationType type, Integer quantity, Long accountId);
}
