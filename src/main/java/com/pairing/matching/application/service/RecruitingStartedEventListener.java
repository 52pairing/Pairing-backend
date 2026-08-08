package com.pairing.matching.application.service;

import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.project.application.event.RecruitingStartedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 착수금 결제 완료(모집 시작) 신호를 받아 포지션별 초기 추천 처리를 건다(project 도메인이 결제
 * 커밋 후 {@link RecruitingStartedEvent}를 발행함, .ai/STATE.md "확정된 설계 결정 4" 참고).
 *
 * <p>결제 트랜잭션이 이미 커밋된 뒤에 도착하므로 AFTER_COMMIT에서 받는다. 포지션 하나가
 * 실패해도(AI 서버 문제 등) 같은 프로젝트의 다른 포지션 처리는 계속돼야 해서 포지션 단위로
 * 예외를 잡는다.
 *
 * <p>실제 저장 로직은 {@link RecruitingStartedPositionHandler}(별도 빈)에 위임한다 — 같은 빈
 * 안에서 자기 자신의 메서드를 호출하면({@code this.method()}) 스프링 프록시를 거치지 않아
 * {@code @Transactional(REQUIRES_NEW)}가 적용되지 않는다(자체 호출 문제). REQUIRES_NEW로
 * 새 트랜잭션을 열지 않으면 커밋된 트랜잭션은 이미 끝난 상태라 저장이 붙지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RecruitingStartedEventListener {

    private final ProjectDirectoryPort projectDirectoryPort;
    private final RecruitingStartedPositionHandler positionHandler;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecruitingStarted(RecruitingStartedEvent event) {
        List<Long> positionIds = projectDirectoryPort.findPositionIds(event.projectId());
        for (Long positionId : positionIds) {
            try {
                positionHandler.startInitialRecommendation(event.projectId(), positionId);
            } catch (Exception e) {
                log.error("[모집 시작 처리 실패] projectId={}, positionId={}", event.projectId(), positionId, e);
            }
        }
    }
}
