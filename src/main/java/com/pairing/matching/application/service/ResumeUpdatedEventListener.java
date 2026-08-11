package com.pairing.matching.application.service;

import com.pairing.freelancer.application.event.ResumeUpdatedEvent;
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
 * <p>실제 조립·전송은 {@link FreelancerEmbeddingRefresher}가 한다 — 조건 저장
 * ({@link ConditionUpdatedEventListener})도 같은 벡터를 다시 만들기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ResumeUpdatedEventListener {

    private final FreelancerEmbeddingRefresher freelancerEmbeddingRefresher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResumeUpdated(ResumeUpdatedEvent event) {
        try {
            freelancerEmbeddingRefresher.refreshByAccountId(event.accountId());
        } catch (Exception e) {
            log.error("[이력서 저장 → 임베딩 재생성 실패] accountId={}", event.accountId(), e);
        }
    }
}
