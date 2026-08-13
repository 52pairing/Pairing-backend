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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * 매칭 도메인이 보내는 알림을 한곳에 모은다. 문구·링크 경로가 여기저기 흩어지면
 * 같은 상황인데 화면마다 다른 말이 나가기 쉬워서다.
 *
 * <p><b>직접 호출하지 말 것.</b> {@link MatchingNotificationListener}가 커밋 후에만 부른다.
 * 매칭 서비스에서 바로 부르면 알림 실패가 매칭을 롤백시킨다 - 근거는
 * {@link com.pairing.matching.application.event.MatchingNotificationRequested} 주석 참고.
 *
 * <p>각 메서드가 {@code REQUIRES_NEW}인 이유는 두 가지다.
 * <ul>
 *   <li>알림 저장 실패를 <b>이 트랜잭션 안에 가둔다.</b> 리스너는 트랜잭션 경계 밖에서 예외를 잡으므로,
 *   실패한 알림만 롤백되고 이미 커밋된 매칭에는 닿지 않는다.</li>
 *   <li>{@code AFTER_COMMIT} 리스너는 커밋이 끝나가는 트랜잭션과 같은 스레드에서 돈다. 새 트랜잭션을
 *   열지 않으면 그 트랜잭션에 합류해서 INSERT가 <b>조용히 버려진다</b>(예외도 로그도 안 남는다).</li>
 * </ul>
 *
 * <p>예외를 여기서 삼키지 않는다. 삼키면 리스너가 실패를 알 수 없고, 알림 도메인의
 * {@code create()}가 이미 트랜잭션을 rollback-only로 찍어둔 상태라 삼키는 것 자체가 소용이 없다.
 * 로그는 리스너가 {@code kind}·{@code requestId}와 함께 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MatchingNotifier {

    private final NotificationCreateUseCase notificationCreateUseCase;
    private final FreelancerDirectoryPort freelancerDirectoryPort;
    private final ProjectDirectoryPort projectDirectoryPort;

    /** 클라이언트가 매칭 요청을 보냈다 -&gt; 받은 프리랜서에게. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

    private void send(Supplier<CreateNotificationCommand> command) {
        CreateNotificationCommand created = command.get();
        // ProjectDirectoryPort.findClientAccountId는 클라이언트 프로필을 못 찾으면 null을 돌려준다.
        // 그대로 넘기면 알림 도메인 안쪽에서 NT_003으로 터지는데, 남의 도메인 에러코드로 나오면
        // 원인을 찾기 어렵다. 여기서 걸러 무엇이 없었는지 분명한 로그를 남긴다.
        //
        // 이 포트를 orElseThrow로 바꾸지 않은 이유: ClientGradeResolver가 같은 포트를 쓰면서
        // 프로필이 없으면 조용히 SILVER로 떨어뜨린다(등급 가중치 0%). 던지게 바꾸면 추천 라운드
        // 생성 자체가 실패하므로, 알림 때문에 그 동작을 바꿀 수는 없다.
        if (created.ownerAccountId() == null) {
            log.warn("[매칭 알림 생략] 받을 계정을 찾지 못했다. type={} link={}",
                    created.type(), created.linkUrl());
            return;
        }
        notificationCreateUseCase.create(created);
    }
}
