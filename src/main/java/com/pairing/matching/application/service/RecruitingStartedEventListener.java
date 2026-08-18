package com.pairing.matching.application.service;

import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.infrastructure.config.MatchingAsyncExecutorConfig;
import com.pairing.project.application.event.RecruitingStartedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
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
 *
 * <p><b>{@code @Async}인 이유.</b> AFTER_COMMIT 리스너는 기본적으로 커밋한 스레드에서 그대로
 * 이어서 실행된다. 즉 이 리스너가 끝날 때까지 <b>착수금 결제 API 응답이 나가지 않는다</b>.
 * 포지션마다 임베딩 생성 + LLM 추천을 부르는데 LLM 읽기 타임아웃만 60초라, 포지션이 여러 개면
 * 결제 화면이 1분 넘게 멈춘다. 결제 결과는 추천 완료 여부와 무관하게 바로 알려줘야 하므로
 * 별도 스레드({@code AsyncConfig.taskExecutor})로 넘긴다.
 *
 * <p>비동기로 넘기면 호출한 쪽은 예외를 볼 수 없다. 그래서 여기서 반드시 로그로 남긴다
 * (이미 포지션 단위로 잡고 있음).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RecruitingStartedEventListener {

    private final ProjectDirectoryPort projectDirectoryPort;
    private final RecruitingStartedPositionHandler positionHandler;
    private final MatchingRoundFiller roundFiller;
    private final MatchingRoundCompletionNotifier completionNotifier;

    @Async(MatchingAsyncExecutorConfig.EXECUTOR_NAME)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecruitingStarted(RecruitingStartedEvent event) {
        List<Long> positionIds = projectDirectoryPort.findPositionIds(event.projectId());
        for (Long positionId : positionIds) {
            try {
                // 1단계: 회차 레코드까지만 만들고 **바로 커밋한다**. 이 시점부터 클라이언트에게
                // "추천 준비중(RUNNING)"이 보인다.
                Long roundId = positionHandler.openInitialRound(event.projectId(), positionId);
                if (roundId == null) {
                    continue;
                }
                // 2단계: AI 호출은 별도 트랜잭션. 실패하면 회차를 FAILED로 닫는다.
                //
                // 한 트랜잭션으로 묶으면 실패 시 회차 행까지 롤백돼 사라진다. 그러면 화면은
                // "준비중"에서 영원히 멈추고, 무엇이 실패했는지 아무 데도 안 남는다.
                fillOrMarkFailed(event.projectId(), positionId, roundId);
            } catch (Exception e) {
                log.error("[모집 시작 처리 실패] projectId={}, positionId={}", event.projectId(), positionId, e);
            }
        }
    }

    private void fillOrMarkFailed(Long projectId, Long positionId, Long roundId) {
        try {
            MatchingRound filled = roundFiller.fill(roundId);
            // 완료 알림이 없으면 프론트는 "준비중" 화면에서 새로고침 전까지 완료를 알 방법이 없다
            // (MatchingRoundCompletionNotifier 참고, 2026-08-18 실사용 중 발견).
            completionNotifier.notifyCompleted(filled);
        } catch (Exception e) {
            log.error("MATCHING_DEBUG java.round.initial.failed projectId={} positionId={} roundId={}",
                    projectId, positionId, roundId, e);
            roundFiller.markFailed(roundId);
            completionNotifier.notifyFailed(projectId, positionId);
        }
    }
}
