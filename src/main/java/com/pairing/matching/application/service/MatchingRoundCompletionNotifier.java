package com.pairing.matching.application.service;

import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.MatchingRoundStatus;
import com.pairing.notification.application.command.CreateNotificationCommand;
import com.pairing.notification.application.usecase.NotificationCreateUseCase;
import com.pairing.notification.domain.model.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최초 추천과 멈춘 회차 복구가 끝났을 때 클라이언트에게 알린다.
 *
 * <p>재추천({@link RerecommendRequestedEventListener})은 처음부터 이 알림을 보냈지만, 최초 추천
 * ({@link RecruitingStartedEventListener})과 멈춘 회차 복구({@link StaleRoundRecoveryService})는
 * 빠져 있었다. 프론트는 이 알림({@code MATCHING_RECOMMENDED})을 받아야만 추천 후보 화면을 다시
 * 불러오고, 그 외엔 폴링하지 않는다 - 그래서 최초 추천을 기다리는 클라이언트는 알림이 안 오면
 * 새로고침 전까지 완료 여부를 알 방법이 없었다(2026-08-18 실사용 중 발견).
 *
 * <p>{@code REQUIRES_NEW}인 이유는 {@link MatchingNotifier}와 같다 - 호출부가 이미 자기
 * 트랜잭션(REQUIRES_NEW)에서 회차를 커밋한 뒤라, 새 트랜잭션을 안 열면 알림 저장이 그 끝난
 * 트랜잭션에 합류해 조용히 버려진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MatchingRoundCompletionNotifier {

    private final NotificationCreateUseCase notificationCreateUseCase;
    private final ProjectDirectoryPort projectDirectoryPort;

    /** 후보가 0명이어도 알린다 - 기다리는 쪽에서는 "아직 처리 중"과 구분이 안 되기 때문이다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void notifyCompleted(MatchingRound round) {
        boolean empty = round.getStatus() == MatchingRoundStatus.EXHAUSTED;
        String title = empty ? "추천할 후보가 더 없습니다." : "새 추천 후보가 준비됐습니다.";
        String content = empty
                ? "조건에 맞는 프리랜서를 모두 추천해 더 보여드릴 후보가 없습니다."
                : "추천 후보 탭에서 확인해보세요.";
        notify(round.getProjectId(), round.getPositionId(), title, content);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void notifyFailed(Long projectId, Long positionId) {
        notify(projectId, positionId, "추천 후보를 만들지 못했습니다.",
                "일시적인 오류로 추천에 실패했어요. 확인 후 다시 시도해주세요.");
    }

    private void notify(Long projectId, Long positionId, String title, String content) {
        Long clientAccountId = projectDirectoryPort.findClientAccountId(projectId);
        if (clientAccountId == null) {
            log.warn("[매칭 후보 완료 알림 생략] 받을 계정을 찾지 못했다. projectId={} positionId={}",
                    projectId, positionId);
            return;
        }
        try {
            notificationCreateUseCase.create(new CreateNotificationCommand(
                    clientAccountId, NotificationType.MATCHING_RECOMMENDED, title, content,
                    "/matchings/positions/" + positionId + "/candidates"));
        } catch (Exception e) {
            log.warn("[매칭 후보 완료 알림 발송 실패 - 무시하고 진행] positionId={}", positionId, e);
        }
    }
}
