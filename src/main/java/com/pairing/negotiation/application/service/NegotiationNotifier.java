package com.pairing.negotiation.application.service;

import com.pairing.negotiation.application.event.NegotiationNotificationRequested;
import com.pairing.negotiation.application.port.out.PartyNameReaderPort;
import com.pairing.negotiation.application.port.out.PartyProfilePort;
import com.pairing.negotiation.application.port.out.ProjectReaderPort;
import com.pairing.notification.application.command.CreateNotificationCommand;
import com.pairing.notification.application.usecase.NotificationCreateUseCase;
import com.pairing.notification.domain.model.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 협상 도메인이 보내는 알림을 한곳에 모은다({@code matching} 의 {@code MatchingNotifier} 와 같은 역할).
 * 문구·링크 경로가 호출부마다 흩어지면 같은 상황인데 화면마다 다른 말이 나가기 쉬워서다.
 *
 * <p><b>"언제 보낼지"는 여기서 정하지 않는다.</b> 그 판단은 협상 로직의 몫이라
 * {@code NegotiationLoopService} 가 이벤트로 알려 준다. 여기서는 "누구에게 / 무슨 문구로 /
 * 어디로 이동"만 정한다.
 *
 * <p><b>커밋 후에 돈다.</b> 이유는 {@link NegotiationNotificationRequested} 주석 참고 — 요약하면
 * 알림 실패가 협상 트랜잭션을 롤백시키지 못하게 하기 위함이다. 여기서 예외를 삼키는 것도 같은
 * 이유이고, 특히 {@code PROPOSED} 는 대리인 비동기 스레드에서 오므로 예외가 새면 성공한 라운드가
 * 대리인 실패로 뒤집힌다.
 *
 * <p>{@code @Async} 는 붙이지 않았다. 알림 저장은 insert 한 건이라 대리인 스레드를 잡아 둘 만한
 * 시간이 아니고, 발송 순서(시작 → 제안)가 그대로 유지되는 편이 낫다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NegotiationNotifier {

    /** 알림을 눌렀을 때 이동할 프론트 경로. 협상방 한 곳이라 3종이 모두 같다. */
    private static final String LINK_PREFIX = "/negotiations/";

    /** 이름을 못 읽었을 때 쓰는 표시. 알림 문구가 "null 님과의" 로 나가는 것보다 낫다. */
    private static final String UNKNOWN_FREELANCER = "프리랜서";
    private static final String UNKNOWN_PROJECT = "프로젝트";

    private final NotificationCreateUseCase notificationCreateUseCase;
    private final PartyProfilePort partyProfilePort;
    private final PartyNameReaderPort partyNameReaderPort;
    private final ProjectReaderPort projectReaderPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(NegotiationNotificationRequested event) {
        try {
            send(event);
        } catch (Exception e) {
            // 여기까지 오는 것은 대상 조회 실패(프로젝트·이름)다. 개별 발송 실패는 push 안에서 잡는다.
            log.warn("[협상 알림 대상 조회 실패 - 알림 없이 진행] negotiationId={}, type={}",
                    event.negotiationId(), event.type(), e);
        }
    }

    private void send(NegotiationNotificationRequested event) {
        ProjectReaderPort.ProjectView project = projectReaderPort.findById(event.projectId()).orElse(null);
        String projectTitle = project != null && project.title() != null ? project.title() : UNKNOWN_PROJECT;
        String freelancerName = partyNameReaderPort.findFreelancerName(event.freelancerProfileId())
                .orElse(UNKNOWN_FREELANCER);

        // 양측에 각각 한 번. 상대가 누구인지로 문구가 갈린다 — 클라는 프리랜서 이름을,
        // 프리랜서는 프로젝트명을 보는 편이 알림 목록에서 구분이 된다.
        push(clientAccountId(project), event, content(event, freelancerName + " 님과의"));
        push(partyProfilePort.findAccountIdByFreelancerProfileId(event.freelancerProfileId()).orElse(null),
                event, content(event, projectTitle + " 프로젝트의"));
    }

    private Long clientAccountId(ProjectReaderPort.ProjectView project) {
        if (project == null) {
            return null;
        }
        return partyProfilePort.findAccountIdByClientProfileId(project.clientProfileId()).orElse(null);
    }

    /**
     * 한 사람에게 보낸다. <b>한쪽 실패가 다른 쪽 발송을 막지 않도록</b> 각각 감싼다 —
     * 한 명이 계정 없이 남은 시험 데이터라도 상대는 알림을 받아야 한다.
     */
    private void push(Long accountId, NegotiationNotificationRequested event, String content) {
        if (accountId == null) {
            log.warn("[협상 알림 수신자 계정을 못 찾아 건너뜀] negotiationId={}, type={}",
                    event.negotiationId(), event.type());
            return;
        }
        try {
            notificationCreateUseCase.create(new CreateNotificationCommand(
                    accountId, event.type(), title(event.type()), content,
                    LINK_PREFIX + event.negotiationId()));
        } catch (Exception e) {
            log.warn("[협상 알림 발송 실패 - 무시하고 진행] negotiationId={}, type={}, accountId={}",
                    event.negotiationId(), event.type(), accountId, e);
        }
    }

    private String title(NotificationType type) {
        return switch (type) {
            case NEGOTIATION_STARTED -> "AI 대리인 협상이 시작되었습니다.";
            case NEGOTIATION_PROPOSED -> "새로운 AI 제안이 도착했습니다.";
            case NEGOTIATION_FAILED -> "협상이 결렬되었습니다.";
            default -> type.getLabel();
        };
    }

    /**
     * {@code subject} 는 받는 사람 기준 "무엇에 대한 협상인지"다(클라 = 상대 이름, 프리 = 프로젝트명).
     * 세 문구가 같은 자리에서 이어지도록 소유격까지 호출부가 붙여서 넘긴다.
     */
    private String content(NegotiationNotificationRequested event, String subject) {
        return switch (event.type()) {
            case NEGOTIATION_STARTED -> subject
                    + " 조건 협상이 시작됐어요. AI 대리인이 첫 제안을 만들고 있습니다.";
            case NEGOTIATION_PROPOSED -> subject + " 협상 " + event.roundNo()
                    + "라운드 제안이 도착했어요. 협상방에서 확인하고 수락 여부를 알려주세요.";
            case NEGOTIATION_FAILED -> subject + " 협상이 종료됐어요: " + endReason(event);
            default -> throw new IllegalStateException("협상 알림이 아닌 타입: " + event.type());
        };
    }

    /** 결렬 사유. 포기는 사용자가 적은 사유, 자동 결렬은 라운드 상한 안내가 들어온다. */
    private String endReason(NegotiationNotificationRequested event) {
        return event.endReason() == null || event.endReason().isBlank()
                ? "협상 종료" : event.endReason();
    }
}
