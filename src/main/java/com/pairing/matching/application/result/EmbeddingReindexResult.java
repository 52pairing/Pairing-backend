package com.pairing.matching.application.result;

/** 임베딩 일괄 재색인 결과 건수. 임베딩 모델 교체처럼 벡터 공간이 바뀌는 상황에 관리자가 수동으로 돌린다. */
public record EmbeddingReindexResult(
        int freelancerSuccessCount,
        int freelancerFailCount,
        int positionSuccessCount,
        int positionFailCount
) {
}
