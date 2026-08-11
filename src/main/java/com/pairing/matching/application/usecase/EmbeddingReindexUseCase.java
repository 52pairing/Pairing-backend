package com.pairing.matching.application.usecase;

import com.pairing.matching.application.result.EmbeddingReindexResult;

public interface EmbeddingReindexUseCase {

    /**
     * 재색인을 백그라운드로 시작하고 바로 돌아온다. 관리자 API가 쓴다.
     *
     * <p>대상 1건마다 외부 AI 호출이 일어나서 전체가 몇 분씩 걸릴 수 있다. 동기로 두면 관리자
     * 화면이 그동안 멈춰 있고, 그 전에 프록시/게이트웨이 타임아웃에 먼저 끊긴다. 결과(성공·실패
     * 건수)는 호출자에게 못 돌려주므로 완료 시점에 로그로 남긴다.
     */
    void startReindexAll();

    /**
     * 이력서 있는 프리랜서 전체 + 모집 시작한 포지션 전체의 임베딩을 다시 생성한다.
     * Gemini 임베딩 모델을 교체했을 때처럼, 기존 벡터를 새 모델 기준으로 다시 만들어야 할 때 쓴다.
     *
     * <p>동기로 끝까지 돌고 결과를 돌려준다. 배치/테스트용이며, 관리자 API는
     * {@link #startReindexAll()}을 쓴다.
     */
    EmbeddingReindexResult reindexAll();
}
