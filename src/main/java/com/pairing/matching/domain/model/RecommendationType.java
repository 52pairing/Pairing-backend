package com.pairing.matching.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 추천 라운드 종류.
 *
 * <p>무료 재추천은 요청한 후보가 전원 거절·만료됐을 때 프로젝트당 1회만 쓸 수 있다.
 * 유료는 최대 5회, 1명당 10,000원이다.
 */
@Getter
@RequiredArgsConstructor
public enum RecommendationType {

    INITIAL("최초 추천"),
    FREE("무료 재추천"),
    PAID("유료 재추천");

    private final String label;
}
