package com.pairing.matching.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 매칭 요청 1건이 종결된 사유.
 *
 * <p>무료 재추천 판정("요청받은 프리랜서 전원이 거절했거나 만료됨")에 이 값을 쓴다.
 * 직접 거절과 자동 만료를 구분해야 해서 상태(MatchingStatus.REJECTED)만으로는 판단할 수 없다.
 */
@Getter
@RequiredArgsConstructor
public enum RejectReason {

    DIRECT_REJECT("직접 거절"),
    EXPIRED("응답 기한 만료"),
    NEGOTIATION_FAILED("협상 결렬");

    private final String label;
}
