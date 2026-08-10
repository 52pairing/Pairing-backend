package com.pairing.matching.application.usecase;

import com.pairing.matching.application.result.EmbeddingReindexResult;

public interface EmbeddingReindexUseCase {

    /**
     * 이력서 있는 프리랜서 전체 + 모집 시작한 포지션 전체의 임베딩을 다시 생성한다.
     * Gemini 임베딩 모델을 교체했을 때처럼, 기존 벡터를 새 모델 기준으로 다시 만들어야 할 때 쓴다.
     */
    EmbeddingReindexResult reindexAll();
}
