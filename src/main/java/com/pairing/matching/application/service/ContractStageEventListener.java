package com.pairing.matching.application.service;

import com.pairing.contract.application.event.ContractSignedEvent;
import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.matching.domain.model.MatchingStatus;
import com.pairing.matching.domain.repository.MatchingRequestRepository;
import com.pairing.project.application.event.ProjectCanceledEvent;
import com.pairing.project.application.event.ProjectClosedEvent;
import com.pairing.project.application.event.ProjectCompletionRequestedEvent;
import com.pairing.settlement.application.event.ProjectProgressStartedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 계약 체결 이후의 인원별 상태 전이(CONTRACTED -&gt; IN_PROGRESS -&gt; COMPLETION_PENDING -&gt; CLOSED).
 * 계약·정산·프로젝트 도메인이 이벤트를 내고 매칭이 자기 애그리거트({@code matching_request})를 옮긴다.
 *
 * <p>전진만 하는 건 아니다. 프로젝트가 취소되면 {@link #onProjectCanceled} 가 응답 대기 중인 요청을
 * 되돌리는 방향으로 종결한다(P46). 남의 이벤트에 맞춰 {@code matching_request} 상태를 옮긴다는 점이
 * 같아서 여기에 뒀다.
 *
 * <p>여기가 없으면 계약을 체결하고 프로젝트가 끝나도 매칭 요청은 {@code CONTRACT_PENDING}에 그대로
 * 남는다(3번이 계약 도메인 붙이며 발견, 2026-08-10).
 *
 * <p><b>{@code @Async}를 붙이지 않는다.</b> 다른 매칭 리스너들과 반대인데, 이건 AI 호출 없이
 * UPDATE 몇 줄만 하는 작업이고 발행 도메인의 트랜잭션과 원자적으로 묶이는 게 맞기 때문이다 —
 * 계약은 체결됐는데 매칭 상태만 안 따라오는 상황을 막으려는 게 이 클래스의 목적이라, 실패하면
 * 차라리 같이 롤백되는 편이 낫다. 같은 이유로 <b>여기에 알림·외부 호출 같은 무거운 작업을 넣으면
 * 안 된다</b>(발행 쪽 트랜잭션을 붙잡고, 실패하면 계약 체결·결제까지 롤백시킨다).
 *
 * <p><b>상태로 좁혀서 조회하는 이유.</b> 프로젝트 단위 이벤트 3개는 "그 프로젝트의 모든 요청"이
 * 대상이지만, 실제로는 거절·만료된 요청도 같은 프로젝트에 섞여 있다. 그것까지
 * {@code advanceStatus}에 넘기면 종결 상태라 예외가 나고, 위 이유로 결제까지 롤백된다.
 * 그래서 직전 단계인 요청만 골라서 옮긴다. 이벤트가 중복 전달돼도 두 번 적용되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ContractStageEventListener {

    private final MatchingRequestRepository matchingRequestRepository;

    /** 양측 서명 완료 -&gt; 그 프리랜서의 요청 1건만 계약 완료로. */
    @EventListener
    public void onContractSigned(ContractSignedEvent event) {
        matchingRequestRepository.findByPositionIdAndFreelancerId(event.positionId(), event.freelancerId())
                .filter(request -> request.getStatus() == MatchingStatus.CONTRACT_PENDING)
                .ifPresentOrElse(
                        request -> advance(request, MatchingStatus.CONTRACTED),
                        () -> log.warn("[계약 체결 - 옮길 매칭 요청 없음] contractId={}, positionId={}, freelancerId={}",
                                event.contractId(), event.positionId(), event.freelancerId()));
    }

    /** 전원 계약 + 착수금 결제 완료 -&gt; 계약 완료 상태인 요청들을 진행중으로. */
    @EventListener
    public void onProjectProgressStarted(ProjectProgressStartedEvent event) {
        advanceAll(event.projectId(), MatchingStatus.CONTRACTED, MatchingStatus.IN_PROGRESS);
    }

    /** 클라이언트가 완료 처리 -&gt; 진행중인 요청들을 완료 대기로. */
    @EventListener
    public void onProjectCompletionRequested(ProjectCompletionRequestedEvent event) {
        advanceAll(event.projectId(), MatchingStatus.IN_PROGRESS, MatchingStatus.COMPLETION_PENDING);
    }

    /** 성공보수 결제 완료 -&gt; 완료 대기인 요청들을 종료로. */
    @EventListener
    public void onProjectClosed(ProjectClosedEvent event) {
        advanceAll(event.projectId(), MatchingStatus.COMPLETION_PENDING, MatchingStatus.CLOSED);
    }

    /**
     * 프로젝트 취소 -&gt; 아직 응답하지 않은 요청을 기한 만료로 종결. (모집 기간 만료 P46)
     *
     * <p><b>{@link #advance} 를 재사용하지 않는다.</b> {@code advanceStatus} 는 상태만 바꾸고
     * {@code rejectReason} 을 비워둔다. 그러면 화면이 프리랜서의 직접 거절과 구분하지 못한다.
     * {@code expire()} 로 사유까지 남긴다.
     *
     * <p><b>{@code MatchingRequestExpirer} 도 쓰지 않는다.</b> 그쪽은 만료 알림을 발행한다.
     * 프로젝트 취소로 종결된 건에 "응답 기한 3일이 지나 자동으로 종료됐어요" 가 나가면 사실과 다르다 —
     * 프리랜서는 응답할 기회가 없었고, 클라이언트는 자기가 접은 건이다. 취소 통지는 알림 도메인이
     * {@code PROJECT_CANCELED} 로 따로 보낸다. {@code syncStage} 도 부르지 않는다 —
     * {@code Project.syncStage} 는 CANCELED 면 그냥 돌아 나와서 어차피 아무 일도 일어나지 않는다.
     *
     * <p>{@code RejectReason.EXPIRED} 를 그대로 쓴다. 프리랜서가 놓친 게 아니라 요청 자체가 기한 안에
     * 결론이 안 난 것이라 "응답 기한 만료" 로 나가도 맞다(3번과 확인, 2026-08-14).
     *
     * <p><b>같은 이벤트를 받는 계약·정산과 달리 발행 쪽 트랜잭션에 합류한다</b>(그쪽은
     * {@code AFTER_COMMIT + REQUIRES_NEW}). {@code accept()} 에는 프로젝트 상태 가드가 없어서
     * ({@code assertRecruiting} 은 요청 <b>발송</b> 경로에만 걸려 있다) 커밋 뒤에 정리하면 그 틈에
     * 수락이 들어올 수 있다. 그러면 {@code startNegotiating} 의 {@code advanceTo} 가 {@code PJ_012} 를
     * 던져 수락이 롤백되는데, 프리랜서 화면에 남의 도메인 에러가 뜬다. 같은 트랜잭션에서 UPDATE 하면
     * 행 잠금이 그 틈을 닫는다. 실패해도 반쯤 처리된 상태가 남지 않고 만료 스케줄러가 다음 주기에
     * 다시 집는다.
     *
     * <p><b>대상이 없는 게 정상이라 {@link #advanceAll} 과 달리 경고를 남기지 않는다.</b> 취소되는
     * 프로젝트 대부분은 응답 대기 중인 요청이 없다.
     */
    @EventListener
    public void onProjectCanceled(ProjectCanceledEvent event) {
        List<MatchingRequest> targets = matchingRequestRepository
                .findByProjectIdAndStatus(event.projectId(), MatchingStatus.REQUEST_PENDING);

        targets.forEach(request -> {
            request.expire();
            matchingRequestRepository.save(request);
        });

        if (!targets.isEmpty()) {
            log.info("[프로젝트 취소] 응답 대기 중인 요청 {}건을 기한 만료로 종결했다. projectId={}",
                    targets.size(), event.projectId());
        }
    }

    private void advanceAll(Long projectId, MatchingStatus from, MatchingStatus to) {
        List<MatchingRequest> targets = matchingRequestRepository.findByProjectIdAndStatus(projectId, from);
        if (targets.isEmpty()) {
            log.warn("[프로젝트 단계 전환 - 대상 없음] projectId={}, {} -> {}", projectId, from, to);
            return;
        }
        targets.forEach(request -> advance(request, to));
    }

    private void advance(MatchingRequest request, MatchingStatus to) {
        request.advanceStatus(to);
        matchingRequestRepository.save(request);
    }
}
