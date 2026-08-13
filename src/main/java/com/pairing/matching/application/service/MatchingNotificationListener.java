package com.pairing.matching.application.service;

import com.pairing.matching.application.event.MatchingNotificationRequested;
import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.matching.domain.repository.MatchingRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 매칭 알림을 커밋 후에 보낸다. 발송 실패가 매칭 데이터에 닿지 않게 하는 것이 이 클래스의 존재 이유다.
 * 근거는 {@link MatchingNotificationRequested} 주석 참고.
 *
 * <p><b>여기에 {@code @Transactional}을 붙이지 않는다.</b> 트랜잭션 경계 안에서 try-catch를 하면
 * 이 클래스가 막으려는 문제가 그대로 재현된다 - 알림 도메인의 {@code create()}가 던진 예외는 그
 * 트랜잭션을 이미 rollback-only로 표시해 놓았으므로, 여기서 잡아도 커밋 시점에
 * {@code UnexpectedRollbackException}이 난다. 그래서 <b>트랜잭션은 {@link MatchingNotifier} 쪽에
 * REQUIRES_NEW로 두고, 잡는 것은 그 경계 밖인 여기서 한다.</b>
 *
 * <p>{@code REQUIRES_NEW}가 왜 꼭 필요한가: {@code AFTER_COMMIT} 리스너는 커밋이 끝나가는
 * 트랜잭션과 같은 스레드에서 이어 실행된다. 새 트랜잭션을 열지 않으면 그 끝나가는 트랜잭션에
 * 합류해서 INSERT가 <b>조용히 버려진다</b>(예외도 로그도 안 남는다). 협상 도메인이 이걸 빠뜨려서
 * 알림이 하나도 저장되지 않고 있었다.
 *
 * <p>{@code @Async}는 붙이지 않는다. 알림 저장은 INSERT 한 건이라 API 응답을 붙잡는 시간이 무시할
 * 수준이고, 비동기로 만들면 예외가 이 스레드로 오지 않아 아래 로그를 남길 수 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MatchingNotificationListener {

    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingNotifier matchingNotifier;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MatchingNotificationRequested event) {
        try {
            // 발행 시점 객체를 싣지 않고 다시 읽는다. 수락 직후 즉시 타결처럼 뒤이어 상태가 바뀌는
            // 경우가 있어서, 실어 보내면 낡은 값으로 문구가 나갈 수 있다.
            MatchingRequest request = matchingRequestRepository.findById(event.requestId()).orElse(null);
            if (request == null) {
                log.warn("[매칭 알림 생략] 대상 요청이 없다. kind={} requestId={}", event.kind(), event.requestId());
                return;
            }
            dispatch(event, request);
        } catch (Exception e) {
            // 알림 실패는 본 기능을 막지 않는다. 다만 조용히 사라지면 안 되므로 error로 남긴다
            // (매칭은 이미 커밋됐고 사용자는 알림만 못 받은 상태라, 로그가 유일한 단서다).
            log.error("[매칭 알림 발송 실패] kind={} requestId={}", event.kind(), event.requestId(), e);
        }
    }

    private void dispatch(MatchingNotificationRequested event, MatchingRequest request) {
        switch (event.kind()) {
            case REQUESTED -> matchingNotifier.notifyRequested(request, event.projectTitle());
            case ACCEPTED -> matchingNotifier.notifyAccepted(request);
            case REJECTED -> matchingNotifier.notifyRejected(request);
            case EXPIRED -> matchingNotifier.notifyExpired(request);
        }
    }
}
