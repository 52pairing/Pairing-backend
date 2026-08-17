package com.pairing.matching.application.service;

import com.pairing.freelancer.application.event.ResumeUpdatedEvent;
import com.pairing.matching.infrastructure.config.MatchingAsyncExecutorConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 이력서 저장 -&gt; 프리랜서 임베딩 재생성. 이력서 저장 자체는 매칭이 모르는 트랜잭션이라
 * {@link ResumeUpdatedEvent}로만 신호를 받는다({@code freelancer.application.service.ResumeService}가 발행).
 *
 * <p>매칭 자신의 DB에 쓰는 게 없어 별도 빈으로 REQUIRES_NEW를 걸 필요가 없다
 * ({@code ProjectUpdatedEventListener}와 같은 이유).
 *
 * <p><b>{@code @Async}인 이유.</b> AFTER_COMMIT 리스너는 커밋한 스레드에서 그대로 이어 실행되므로,
 * 이대로 두면 <b>프리랜서의 이력서 저장 API 응답이 임베딩 생성(외부 AI 호출)까지 기다린다</b>.
 * 임베딩은 저장 결과와 무관하게 뒤에서 만들면 되는 값이라 별도 스레드로 넘긴다.
 *
 * <p>실제 조립·전송은 {@link FreelancerEmbeddingRefresher}가 한다 — 관리자 일괄 재색인
 * ({@link EmbeddingReindexService})도 같은 조립을 써야 벡터가 갈리지 않는다.
 *
 * <p><b>조건(스킬·단가·근무조건) 저장은 이 흐름을 타지 않는다.</b> 임베딩 텍스트에 문장만
 * 들어가므로 조건을 바꿔도 같은 벡터가 나온다 — `.ai/STATE.md` "매칭 파이프라인 재설계" 참고.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ResumeUpdatedEventListener {

    private final FreelancerEmbeddingRefresher freelancerEmbeddingRefresher;

    @Async(MatchingAsyncExecutorConfig.EXECUTOR_NAME)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResumeUpdated(ResumeUpdatedEvent event) {
        try {
            freelancerEmbeddingRefresher.refreshByAccountId(event.accountId());
        } catch (Exception e) {
            log.error("[이력서 저장 → 임베딩 재생성 실패] accountId={}", event.accountId(), e);
        }
    }
}
