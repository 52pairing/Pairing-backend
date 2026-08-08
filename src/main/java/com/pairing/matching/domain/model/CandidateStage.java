package com.pairing.matching.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 후보가 매칭 파이프라인에서 도달한 단계.
 *
 * <p>후보 1건은 회차 안에서 이 단계를 따라 진행하며 되돌아가지 않는다.
 * EMBEDDING(1차 추림) -> LLM_FINAL(LLM 최종 선정) -> GUARD(가드 AI 검증 완료).
 */
@Getter
@RequiredArgsConstructor
public enum CandidateStage {

    EMBEDDING("1차 추림"),
    LLM_FINAL("LLM 최종 선정"),
    GUARD("가드 검증 완료");

    private final String label;
}
