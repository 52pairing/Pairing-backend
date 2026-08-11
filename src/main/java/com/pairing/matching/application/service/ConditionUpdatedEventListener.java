package com.pairing.matching.application.service;

import com.pairing.freelancer.application.event.ConditionUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 조건 저장 -&gt; 프리랜서 임베딩 재생성. 직무·스킬·경력·근무방식·기간이 임베딩 텍스트에
 * 들어가므로(2026-08-11부터, {@link FreelancerEmbeddingTextBuilder} 참고) 조건을 고치면 저장된
 * 벡터가 낡는다. 그 전에는 자기소개+경력사항만 넣어서 조건을 바꿔도 벡터가 그대로였고, 그래서
 * 이 리스너도 없었다.
 *
 * <p>{@code @Async} + AFTER_COMMIT인 이유는 {@link ResumeUpdatedEventListener}와 같다 — 안 붙이면
 * 조건 저장 API 응답이 임베딩 생성(외부 AI 호출)까지 기다린다. 매칭 자신의 DB에 쓰는 게 없어
 * 별도 빈으로 REQUIRES_NEW를 걸 필요는 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ConditionUpdatedEventListener {

    private final FreelancerEmbeddingRefresher freelancerEmbeddingRefresher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConditionUpdated(ConditionUpdatedEvent event) {
        try {
            freelancerEmbeddingRefresher.refreshByAccountId(event.accountId());
        } catch (Exception e) {
            log.error("[조건 저장 → 임베딩 재생성 실패] accountId={}", event.accountId(), e);
        }
    }
}
