package com.pairing.negotiation.application.event;

import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.notification.domain.model.NotificationType;

/**
 * 협상 알림 발행 요청. {@code NegotiationNotifier} 가 <b>커밋 후</b> 받아 실제 알림을 만든다.
 *
 * <p><b>왜 알림 유스케이스를 바로 부르지 않는가.</b> {@code NotificationCreateUseCase.create} 는
 * {@code @Transactional}(REQUIRED)이라 협상 트랜잭션에 얹힌다. 그 안에서 예외가 나면 스프링이
 * 공유 트랜잭션을 <b>rollback-only 로 표시</b>하므로, 호출부에서 예외를 삼켜도 협상 커밋이
 * {@code UnexpectedRollbackException} 으로 실패한다 — 알림 한 건 때문에 협상이 롤백된다.
 * 수신자 계정을 못 찾아 {@code NT_003} 이 나는 것만으로도 그렇게 된다.
 * 커밋 후로 미루면 협상은 이미 확정돼 있어 알림이 어떻게 실패해도 되돌릴 것이 없다.
 *
 * <p>협상 상태를 <b>스냅샷으로 담아</b> 보낸다. 커밋 후 다시 읽으면 그 사이 다음 라운드가 돌아
 * 라운드 번호나 종료 사유가 이미 달라져 있을 수 있다.
 */
public record NegotiationNotificationRequested(
        Long negotiationId,
        NotificationType type,
        Long projectId,
        Long freelancerProfileId,
        int roundNo,
        String endReason
) {

    /** 양측 마지노선이 모두 모여 대리인 협상이 시작됐다. */
    public static NegotiationNotificationRequested started(Negotiation negotiation) {
        return of(negotiation, NotificationType.NEGOTIATION_STARTED);
    }

    /** 대리인이 이번 라운드 제안을 만들었다. */
    public static NegotiationNotificationRequested proposed(Negotiation negotiation) {
        return of(negotiation, NotificationType.NEGOTIATION_PROPOSED);
    }

    /** 협상 포기 또는 라운드 상한 소진으로 결렬됐다. */
    public static NegotiationNotificationRequested failed(Negotiation negotiation) {
        return of(negotiation, NotificationType.NEGOTIATION_FAILED);
    }

    private static NegotiationNotificationRequested of(Negotiation negotiation, NotificationType type) {
        return new NegotiationNotificationRequested(negotiation.getId(), type, negotiation.getProjectId(),
                negotiation.getFreelancerId(), negotiation.getTotalRound(), negotiation.getEndReason());
    }
}
