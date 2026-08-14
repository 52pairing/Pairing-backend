package com.pairing.negotiation.application.service;

import com.pairing.negotiation.application.usecase.NegotiationProjectOutcomeUseCase;
import com.pairing.project.application.event.ProjectCanceledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 프로젝트 도메인 이벤트를 받아 협상을 정리한다(project → negotiation). 매칭의
 * {@code ContractStageEventListener} 와 같은 역할이지만, 협상 도메인엔 프로젝트 이벤트 수신부가
 * 없어 새로 둔다.
 *
 * <p><b>AFTER_COMMIT</b>: 프로젝트가 실제로 취소(커밋)된 뒤에만 협상을 결렬시킨다. 같은 트랜잭션으로
 * 묶으면(@EventListener) 여기서 A2A·알림이 얽혀 예외가 날 때 프로젝트 취소까지 롤백되므로 분리한다.
 * 실제 결렬 작업은 {@link NegotiationProjectOutcomeUseCase} 가 {@code REQUIRES_NEW} 로 새 트랜잭션에서
 * 수행한다(AFTER_COMMIT 시점엔 걸린 트랜잭션이 없다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class NegotiationProjectEventListener {

    private final NegotiationProjectOutcomeUseCase projectOutcomeUseCase;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectCanceled(ProjectCanceledEvent event) {
        log.info("[프로젝트 취소] 진행 중 협상 결렬 처리 시작: projectId={}", event.projectId());
        projectOutcomeUseCase.failInProgressForCanceledProject(event.projectId());
    }
}
