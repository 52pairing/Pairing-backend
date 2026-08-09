package com.pairing.matching.application.service;

import com.pairing.freelancer.application.event.ResumeUpdatedEvent;
import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.port.out.MatchingPort;
import com.pairing.matching.application.result.FreelancerResumeSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 이력서 저장 -&gt; 프리랜서 임베딩 재생성. 이력서 저장 자체는 매칭이 모르는 트랜잭션이라
 * {@link ResumeUpdatedEvent}로만 신호를 받는다({@code freelancer.application.service.ResumeService}가 발행).
 *
 * <p>매칭 자신의 DB에 쓰는 게 없어 별도 빈으로 REQUIRES_NEW를 걸 필요가 없다
 * ({@code ProjectUpdatedEventListener}와 같은 이유).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ResumeUpdatedEventListener {

    private final FreelancerDirectoryPort freelancerDirectoryPort;
    private final MatchingPort matchingPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResumeUpdated(ResumeUpdatedEvent event) {
        try {
            Long freelancerId = freelancerDirectoryPort.resolveFreelancerId(event.accountId());
            FreelancerResumeSummary summary = freelancerDirectoryPort.findResumeSummary(freelancerId);
            matchingPort.upsertFreelancerEmbedding(freelancerId, FreelancerEmbeddingTextBuilder.buildText(summary));
        } catch (Exception e) {
            log.error("[이력서 저장 → 임베딩 재생성 실패] accountId={}", event.accountId(), e);
        }
    }
}
