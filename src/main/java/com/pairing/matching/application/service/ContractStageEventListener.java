package com.pairing.matching.application.service;

import com.pairing.contract.application.event.ContractSignedEvent;
import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.matching.domain.model.MatchingStatus;
import com.pairing.matching.domain.repository.MatchingRequestRepository;
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
