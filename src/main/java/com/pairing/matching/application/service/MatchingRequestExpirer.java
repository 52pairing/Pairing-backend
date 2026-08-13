package com.pairing.matching.application.service;

import com.pairing.matching.application.event.MatchingNotificationRequested;
import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.matching.domain.model.MatchingStatus;
import com.pairing.matching.domain.repository.MatchingRequestRepository;
import com.pairing.project.application.usecase.ProjectCommandUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 매칭 요청 1건을 응답 기한 만료로 정리한다. 항상 자기만의 트랜잭션에서 커밋한다.
 *
 * <p>두 호출부 모두 이 독립 트랜잭션이 필요하다.
 * <ul>
 *   <li>{@code MatchingRequestService.accept()/reject()} — 기한이 지난 요청을 그 자리에서 발견하면
 *   만료로 정리한 뒤 바로 400을 던지는데, 그 예외가 accept()/reject() 자신의 트랜잭션을 롤백시킨다.
 *   REQUIRES_NEW로 따로 커밋해야 만료 처리가 그 롤백에 같이 휩쓸리지 않는다.</li>
 *   <li>{@code MatchingRequestService.expireOverdueRequests()}(스케줄러) — 여러 건을 한 트랜잭션에서
 *   처리하면 한 건의 예외가 나머지 건의 커밋까지 위태롭게 한다. 건별로 독립 커밋해야 "한 건 실패해도
 *   나머지는 처리한다"는 말이 실제로 성립한다.</li>
 * </ul>
 *
 * <p>자기 자신 호출이 아니라 진짜 다른 빈 호출로 거쳐야 REQUIRES_NEW가 실제로 적용된다(스프링
 * 프록시를 거쳐야 하기 때문) — {@code RecruitingStartedPositionHandler}와 같은 이유로 분리했다.
 */
@Component
@RequiredArgsConstructor
class MatchingRequestExpirer {

    private static final List<MatchingStatus> NEGOTIATING_STATUSES =
            List.of(MatchingStatus.ACCEPTED, MatchingStatus.NEGOTIATING);
    private static final List<MatchingStatus> CONTRACT_PENDING_STATUSES =
            List.of(MatchingStatus.CONTRACT_PENDING, MatchingStatus.CONTRACTED, MatchingStatus.IN_PROGRESS,
                    MatchingStatus.COMPLETION_PENDING);

    private final MatchingRequestRepository matchingRequestRepository;
    private final ProjectCommandUseCase projectCommandUseCase;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void expireNow(MatchingRequest request) {
        request.expire();
        matchingRequestRepository.save(request);

        Long projectId = request.getProjectId();
        boolean hasContractPending = matchingRequestRepository.existsByProjectIdAndStatusIn(
                projectId, CONTRACT_PENDING_STATUSES);
        boolean hasNegotiating = matchingRequestRepository.existsByProjectIdAndStatusIn(
                projectId, NEGOTIATING_STATUSES);
        projectCommandUseCase.syncStage(projectId, hasContractPending, hasNegotiating);

        // 스케줄러가 처리하든 수락/거절 시도 중에 발견되든 여기를 거치므로, 알림도 여기서 한 번만 보낸다.
        //
        // 발송은 이 트랜잭션이 커밋된 뒤에 별도로 한다. 여기서 직접 보내면 알림 실패가 방금 한 만료
        // 처리를 되돌린다 - 그러면 그 요청이 PENDING으로 남아 10분 주기마다 같은 실패를 반복하고,
        // 무료 재추천 판정(P41)이 "아직 진행 중"으로 잡혀 클라이언트가 재추천을 못 쓰게 된다.
        eventPublisher.publishEvent(MatchingNotificationRequested.expired(request.getId()));
    }
}
