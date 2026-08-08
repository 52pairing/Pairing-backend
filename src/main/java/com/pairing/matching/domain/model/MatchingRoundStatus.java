package com.pairing.matching.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 매칭 회차(포지션 단위 재추천)의 실행 상태. */
@Getter
@RequiredArgsConstructor
public enum MatchingRoundStatus {

    RUNNING("진행중"),
    COMPLETED("완료"),
    FAILED("실패"),
    EXHAUSTED("후보 소진");

    private final String label;
}
