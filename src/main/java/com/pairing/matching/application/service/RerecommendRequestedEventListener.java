package com.pairing.matching.application.service;

import com.pairing.matching.application.event.RerecommendRequestedEvent;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.MatchingRoundStatus;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import com.pairing.notification.application.command.CreateNotificationCommand;
import com.pairing.notification.application.usecase.NotificationCreateUseCase;
import com.pairing.notification.domain.model.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
 * <p>비동기라 예외가 호출자에게 가지 않는다. 실패하면 회차만 남고 후보가 안 채워지므로
 * 반드시 로그로 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RerecommendRequestedEventListener {

    private final MatchingRoundRepository matchingRoundRepository;
    private final MatchingRoundCreationService matchingRoundCreationService;
    private final NotificationCreateUseCase notificationCreateUseCase;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // 원 트랜잭션은 이미 끝났으므로 새 트랜잭션을 열어야 후보 저장이 커밋된다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRerecommendRequested(RerecommendRequestedEvent event) {
        try {
            MatchingRound round = matchingRoundRepository.findById(event.roundId()).orElseThrow();
            MatchingRound filled = matchingRoundCreationService.fillCandidates(round);
            notifyCompleted(event.clientAccountId(), filled);
        } catch (Exception e) {
            log.error("[재추천 후보 생성 실패] roundId={}", event.roundId(), e);
        }
    }

    private void notifyCompleted(Long clientAccountId, MatchingRound round) {
        // 후보가 0명이어도 알린다 — 기다리는 쪽에서는 "아직 처리 중"과 구분이 안 되기 때문이다.
        boolean empty = round.getStatus() == MatchingRoundStatus.EXHAUSTED;
        String title = empty ? "추천할 후보가 더 없습니다." : "새 추천 후보가 준비됐습니다.";
        String content = empty
                ? "조건에 맞는 프리랜서를 모두 추천해 더 보여드릴 후보가 없습니다."
                : "추천 후보 탭에서 확인해보세요.";
        notificationCreateUseCase.create(new CreateNotificationCommand(
                clientAccountId,
                NotificationType.MATCHING_RECOMMENDED,
                title,
                content,
                "/matchings/positions/" + round.getPositionId() + "/candidates"
        ));
    }
}
