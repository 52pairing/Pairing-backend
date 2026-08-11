package com.pairing.matching.application.service;

import com.pairing.matching.application.event.RerecommendRequestedEvent;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.MatchingRoundStatus;
import com.pairing.notification.application.command.CreateNotificationCommand;
import com.pairing.notification.application.usecase.NotificationCreateUseCase;
import com.pairing.notification.domain.model.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 재추천 회차에 실제 후보를 채운다(AI 호출). 재추천 API는 검증·회차 생성까지만 하고 202로 바로
 * 응답하므로, 결과가 준비되면 알림으로 알려줘야 클라이언트가 화면을 갱신할 수 있다.
 *
 * <p>{@code @Async} + AFTER_COMMIT인 이유는 {@code RecruitingStartedEventListener}와 같다 —
 * 붙이지 않으면 재추천 API 응답이 AI 호출을 그대로 기다리게 된다. 커밋 후에 받아야 비동기
 * 스레드에서 방금 만든 회차를 조회할 수 있다.
 *
 * <p>트랜잭션은 {@link RerecommendRoundFiller}(별도 빈, REQUIRES_NEW)가 잡는다 — 성공 경로와 실패
 * 처리가 서로 다른 트랜잭션이어야 실패 시 FAILED 저장이 같이 롤백되지 않는다.
 *
 * <p><b>성공하든 실패하든 반드시 알림을 보낸다.</b> 안 보내면 클라이언트가 "추천 생성 중" 화면에서
 * 오지 않을 알림을 계속 기다린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RerecommendRequestedEventListener {

    private final RerecommendRoundFiller rerecommendRoundFiller;
    private final NotificationCreateUseCase notificationCreateUseCase;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRerecommendRequested(RerecommendRequestedEvent event) {
        try {
            MatchingRound filled = rerecommendRoundFiller.fill(event.roundId());
            notifyCompleted(event.clientAccountId(), filled);
        } catch (Exception e) {
            log.error("[재추천 후보 생성 실패] roundId={}", event.roundId(), e);
            handleFailure(event);
        }
    }

    private void notifyCompleted(Long clientAccountId, MatchingRound round) {
        // 후보가 0명이어도 알린다 — 기다리는 쪽에서는 "아직 처리 중"과 구분이 안 되기 때문이다.
        boolean empty = round.getStatus() == MatchingRoundStatus.EXHAUSTED;
        String title = empty ? "추천할 후보가 더 없습니다." : "새 추천 후보가 준비됐습니다.";
        String content = empty
                ? "조건에 맞는 프리랜서를 모두 추천해 더 보여드릴 후보가 없습니다."
                : "추천 후보 탭에서 확인해보세요.";
        notify(clientAccountId, round.getPositionId(), title, content);
    }

    /**
     * 회차를 FAILED로 닫고 실패를 알린다. FAILED 회차는 재추천 한도 계산에서 빠지므로
     * ({@code MatchingRoundRepository.countByProjectIdAndRoundType} 참고) 무료 1회가 되살아난다.
     */
    private void handleFailure(RerecommendRequestedEvent event) {
        try {
            MatchingRound failed = rerecommendRoundFiller.markFailed(event.roundId());
            notify(event.clientAccountId(), failed.getPositionId(), "추천 후보를 만들지 못했습니다.",
                    "일시적인 오류로 추천에 실패했어요. 잠시 후 다시 시도해주세요. "
                            + "사용한 재추천 횟수는 차감되지 않습니다.");
        } catch (Exception e) {
            // 여기서 또 실패하면 더 할 수 있는 게 없다. 회차가 RUNNING으로 남으므로 로그로 남긴다.
            log.error("[재추천 실패 처리 중 오류 - 회차가 RUNNING으로 남음] roundId={}", event.roundId(), e);
        }
    }

    private void notify(Long clientAccountId, Long positionId, String title, String content) {
        try {
            notificationCreateUseCase.create(new CreateNotificationCommand(
                    clientAccountId, NotificationType.MATCHING_RECOMMENDED, title, content,
                    "/matchings/positions/" + positionId + "/candidates"));
        } catch (Exception e) {
            log.warn("[재추천 알림 발송 실패 - 무시하고 진행] positionId={}", positionId, e);
        }
    }
}
