package com.pairing.matching.application.service;

import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.notification.application.command.CreateNotificationCommand;
import com.pairing.notification.application.usecase.NotificationCreateUseCase;
import com.pairing.notification.domain.model.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 매칭 도메인이 보내는 알림을 한곳에 모은다. 문구·링크 경로가 여기저기 흩어지면
 * 같은 상황인데 화면마다 다른 말이 나가기 쉬워서다.
 *
 * <p>알림 저장·push는 notification 도메인이 알아서 한다(push는 호출한 쪽 트랜잭션이 커밋된 뒤에
 * 나간다). 여기서는 "누구에게 / 어떤 종류 / 어디로 이동"만 정한다.
 *
 * <p><b>알림 실패가 본 기능을 막지 않는다.</b> 매칭 요청은 정상 처리됐는데 알림 한 건 때문에
 * 500이 나가면 안 되므로 예외를 삼키고 로그만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MatchingNotifier {

    private final NotificationCreateUseCase notificationCreateUseCase;
    private final FreelancerDirectoryPort freelancerDirectoryPort;
    private final ProjectDirectoryPort projectDirectoryPort;

    /** 클라이언트가 매칭 요청을 보냈다 -&gt; 받은 프리랜서에게. */
    void notifyRequested(MatchingRequest request, String projectTitle) {
        send(() -> new CreateNotificationCommand(
                freelancerDirectoryPort.resolveAccountId(request.getFreelancerId()),
                NotificationType.MATCHING_REQUESTED,
                "새 프로젝트 제안이 도착했습니다.",
                projectTitle + " 프로젝트에서 함께할 프리랜서로 제안받았어요. 3일 안에 응답해주세요.",
                "/matchings/requests/" + request.getId()
        ));
    }

    /** 프리랜서가 수락했다 -&gt; 요청을 보낸 클라이언트에게. */
    void notifyAccepted(MatchingRequest request) {
        send(() -> new CreateNotificationCommand(
                projectDirectoryPort.findClientAccountId(request.getProjectId()),
                NotificationType.MATCHING_ACCEPTED,
                freelancerName(request) + " 님이 매칭 요청을 수락했습니다.",
                "협상이 시작됐어요. 협상방에서 조건을 확인해보세요.",
                "/matchings/requests/" + request.getId()
        ));
    }

    /** 프리랜서가 직접 거절했다 -&gt; 요청을 보낸 클라이언트에게. */
    void notifyRejected(MatchingRequest request) {
        send(() -> new CreateNotificationCommand(
                projectDirectoryPort.findClientAccountId(request.getProjectId()),
                NotificationType.MATCHING_REJECTED,
                freelancerName(request) + " 님이 매칭 요청을 거절했습니다.",
                "다른 후보에게 요청을 보내거나 재추천을 사용할 수 있어요.",
                "/matchings/requests/" + request.getId()
        ));
    }

    /**
     * 응답 기한(3일)이 지나 자동 만료됐다 -&gt; 요청을 보낸 클라이언트에게.
     * 직접 거절과 같은 {@code MATCHING_REJECTED} 타입이지만 문구로 구분한다(응답에서는 rejectReason으로 구분).
     */
    void notifyExpired(MatchingRequest request) {
        send(() -> new CreateNotificationCommand(
                projectDirectoryPort.findClientAccountId(request.getProjectId()),
                NotificationType.MATCHING_REJECTED,
                freelancerName(request) + " 님이 응답 기한 내에 회신하지 않았습니다.",
                "응답 기한 3일이 지나 자동으로 종료됐어요. 다른 후보를 확인해보세요.",
                "/matchings/requests/" + request.getId()
        ));
    }

    private String freelancerName(MatchingRequest request) {
        return freelancerDirectoryPort.findCardSummary(request.getFreelancerId()).name();
    }

    private void send(java.util.function.Supplier<CreateNotificationCommand> command) {
        try {
            notificationCreateUseCase.create(command.get());
        } catch (Exception e) {
            log.warn("[매칭 알림 발송 실패 - 무시하고 진행]", e);
        }
    }
}
