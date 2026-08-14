package com.pairing.matching.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.freelancer.presentation.api.response.FreelancerConditionResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.exception.BusinessException;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.matching.application.command.CreateNegotiationCommand;
import com.pairing.matching.application.event.MatchingNotificationRequested;
import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.port.out.NegotiationPort;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.ProjectPositionSummary;
import com.pairing.matching.application.usecase.MatchingNegotiationOutcomeUseCase;
import com.pairing.matching.application.usecase.MatchingRequestCommandUseCase;
import com.pairing.matching.application.usecase.MatchingRequestQueryUseCase;
import com.pairing.matching.domain.model.MatchingCandidate;
import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.matching.domain.model.MatchingRequestTab;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.MatchingSnapshot;
import com.pairing.matching.domain.model.MatchingStatus;
import com.pairing.matching.domain.model.SnapshotType;
import com.pairing.matching.domain.repository.MatchingCandidateRepository;
import com.pairing.matching.domain.repository.MatchingRequestRepository;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import com.pairing.matching.domain.repository.MatchingSnapshotRepository;
import com.pairing.matching.exception.MatchingErrorCode;
import com.pairing.matching.presentation.api.response.CandidateProfileSnapshotResponse;
import com.pairing.matching.presentation.api.response.MatchingRequestResponse;
import com.pairing.project.application.usecase.ProjectCommandUseCase;
import com.pairing.project.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
// 협상 결과 반영(MatchingNegotiationOutcomeUseCase)은 MatchingNegotiationOutcomeService가 맡는다 —
// 여기서 같이 구현하면 negotiation 도메인과 빈 생성이 순환한다(그 클래스 주석 참고).
public class MatchingRequestService implements MatchingRequestCommandUseCase, MatchingRequestQueryUseCase {

    private static final List<MatchingStatus> NEGOTIATING_STATUSES =
            List.of(MatchingStatus.ACCEPTED, MatchingStatus.NEGOTIATING);
    private static final List<MatchingStatus> CONTRACT_PENDING_STATUSES =
            List.of(MatchingStatus.CONTRACT_PENDING, MatchingStatus.CONTRACTED, MatchingStatus.IN_PROGRESS,
                    MatchingStatus.COMPLETION_PENDING);

    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingCandidateRepository matchingCandidateRepository;
    private final MatchingRoundRepository matchingRoundRepository;
    private final MatchingSnapshotRepository matchingSnapshotRepository;
    private final ObjectMapper objectMapper;
    private final ProjectDirectoryPort projectDirectoryPort;
    private final FreelancerDirectoryPort freelancerDirectoryPort;
    private final NegotiationPort negotiationPort;
    private final ProjectCommandUseCase projectCommandUseCase;
    private final BudgetCapCalculator budgetCapCalculator;
    private final MatchingRequestResponseAssembler matchingRequestResponseAssembler;
    private final MatchingRequestExpirer matchingRequestExpirer;
    // 알림은 커밋 후에 별도 트랜잭션으로 나간다. MatchingNotifier를 여기서 직접 부르면 알림 실패가
    // 이 서비스의 트랜잭션을 롤백시킨다 - MatchingNotificationRequested 주석 참고.
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public List<MatchingRequestResponse> sendRequests(Long positionId, List<Long> candidateIds, Long accountId) {
        int headcount = projectDirectoryPort.findHeadcount(positionId);
        long activeCount = matchingRequestRepository.countByPositionIdAndStatusNotIn(positionId,
                MatchingStatus.SLOT_RELEASED);
        if (activeCount + candidateIds.size() > headcount) {
            throw new BusinessException(MatchingErrorCode.HEADCOUNT_EXCEEDED);
        }

        return candidateIds.stream()
                .map(candidateId -> sendOneRequest(positionId, candidateId, accountId))
                .toList();
    }

    private MatchingRequestResponse sendOneRequest(Long positionId, Long candidateId, Long accountId) {
        MatchingCandidate candidate = matchingCandidateRepository.findById(candidateId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.CANDIDATE_NOT_FOUND));
        if (!candidate.getPositionId().equals(positionId)) {
            throw new BusinessException(MatchingErrorCode.INVALID_MATCHING_STATE);
        }
        // 노출된 후보만 고를 수 있다(P07). candidateId를 직접 넣으면 가드 탈락자·대기 순번·이미 내린
        // 후보에게도 요청이 나가서 Stage F 가드가 무력화된다(MatchingCandidate.isSelectable 주석 참고).
        if (!candidate.isSelectable()) {
            throw new BusinessException(MatchingErrorCode.CANDIDATE_NOT_SELECTABLE);
        }

        MatchingRound round = matchingRoundRepository.findById(candidate.getRoundId())
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.ROUND_NOT_FOUND));
        Long projectId = round.getProjectId();
        if (!projectDirectoryPort.isOwnedByAccount(projectId, accountId)) {
            throw new BusinessException(GlobalErrorCode.ACCESS_DENIED);
        }
        assertRecruiting(projectId);
        if (matchingRequestRepository.findByPositionIdAndFreelancerId(positionId, candidate.getFreelancerId())
                .isPresent()) {
            throw new BusinessException(MatchingErrorCode.INVALID_MATCHING_STATE);
        }

        MatchingRequest request = MatchingRequest.create(projectId, positionId, candidateId,
                candidate.getFreelancerId());
        request = matchingRequestRepository.save(request);

        MatchingRequestResponse response = matchingRequestResponseAssembler.build(request, accountId);
        eventPublisher.publishEvent(
                MatchingNotificationRequested.requested(request.getId(), response.projectTitle()));
        return response;
    }

    @Override
    @Transactional
    public MatchingRequestResponse accept(Long requestId, Long accountId) {
        MatchingRequest request = getOwnedByFreelancer(requestId, accountId);
        expireIfOverdue(request);
        request.accept();

        // **스냅샷은 여기서 찍지 않고, 찍혀 있는 것을 읽어 쓴다(2026-08-14).**
        //
        // 프리랜서 스냅샷은 후보가 화면에 노출되는 순간에만 찍는다
        // ({@code MatchingRoundCreationService.saveFreelancerSnapshots}). 협상도 <b>그때 얼린 조건</b>으로
        // 시작한다 — 클라이언트는 그 값을 보고 후보를 고른 것이므로, 추천된 뒤 프리랜서가 단가를 올렸다고
        // 협상 출발점이 따라 올라가면 클라이언트가 동의한 적 없는 숫자에서 협상이 시작된다.
        FreelancerConditionResponse condition = exposedConditionOf(request);

        ProjectPositionSummary position = projectDirectoryPort.findPositionSummary(request.getProjectId(),
                request.getPositionId());
        long budgetCap = budgetCapCalculator.calculate(request.getProjectId(), position.budgetAmount(),
                position.totalHeadcount(), position.periodValue(), position.periodUnit());

        // 협상 생성보다 먼저 NEGOTIATING을 저장한다. 조건 불일치가 0개면 협상 도메인이 그 안에서
        // 즉시 타결까지 끝내면서 이 요청을 CONTRACT_PENDING으로 올리는데(markNegotiationAgreed),
        // 순서가 반대면 그 결과를 아래 advanceStatus/save가 NEGOTIATING으로 덮어써 버린다.
        // 게다가 agreeNegotiation()은 NEGOTIATING을 요구하므로, 저장 전에 호출되면
        // INVALID_MATCHING_STATE가 나서 수락 자체가 롤백된다(3번·5번과 확인, 2026-08-10).
        request.advanceStatus(MatchingStatus.NEGOTIATING);
        matchingRequestRepository.save(request);

        negotiationPort.createNegotiation(new CreateNegotiationCommand(
                request.getId(), request.getProjectId(), request.getPositionId(), request.getFreelancerId(),
                budgetCap, condition.payUnit(), condition.payAmount(), condition.workStyle(), condition.workForm(),
                condition.availableFrom(), condition.minAcceptAmount(), condition.startNegotiable(),
                condition.periodValue(), condition.periodUnit()));

        // 프로젝트 상태는 전진만 하므로(이미 계약 대기면 아무 일도 안 함) 즉시 타결 뒤에 불러도 안전하다.
        projectCommandUseCase.startNegotiating(request.getProjectId());

        // 응답은 다시 읽어서 만든다. 즉시 타결이면 위 createNegotiation 안에서 이 요청이
        // CONTRACT_PENDING까지 올라가는데, 그건 별도 조회·저장이라 여기 있는 request 객체에는
        // 반영되지 않는다. 그대로 쓰면 DB는 CONTRACT_PENDING인데 응답만 NEGOTIATING으로 나간다.
        MatchingRequest latest = matchingRequestRepository.findById(request.getId())
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.REQUEST_NOT_FOUND));
        eventPublisher.publishEvent(MatchingNotificationRequested.accepted(latest.getId()));
        return matchingRequestResponseAssembler.build(latest, accountId);
    }

    /**
     * 협상의 출발 조건. <b>후보로 노출된 시점에 얼려둔 값</b>을 쓴다.
     *
     * <p>클라이언트는 그 값을 보고 후보를 골랐다. 추천된 뒤 프리랜서가 희망 단가를 올렸다고 협상
     * 출발점이 따라 올라가면, 클라이언트는 <b>본 적도 동의한 적도 없는 숫자</b>에서 협상을 시작한다.
     *
     * <p>스냅샷이 없으면 현재 조건으로 대체한다. 이 기능 배포 전에 이미 노출된 후보이거나, 노출 시점
     * 캡처가 실패한 경우다(캡처는 매칭을 막지 않도록 실패를 삼킨다). 그때는 협상을 못 시작하게 막는
     * 것보다 현재 값으로 진행하는 편이 낫다.
     */
    private FreelancerConditionResponse exposedConditionOf(MatchingRequest request) {
        return matchingSnapshotRepository.findByFreelancerIdAndPositionIdAndSnapshotType(
                        request.getFreelancerId(), request.getPositionId(), SnapshotType.FREELANCER)
                .map(this::readSnapshotCondition)
                .filter(Objects::nonNull)
                .orElseGet(() -> {
                    log.info("MATCHING_DEBUG java.request.accept.condition_source requestId={} freelancerId={} "
                                    + "source=LIVE reason=snapshot_absent",
                            request.getId(), request.getFreelancerId());
                    return freelancerDirectoryPort.findCondition(request.getFreelancerId());
                });
    }

    private FreelancerConditionResponse readSnapshotCondition(MatchingSnapshot snapshot) {
        try {
            return objectMapper.readValue(snapshot.getSnapshotJson(),
                    CandidateProfileSnapshotResponse.SnapshotPayload.class).condition();
        } catch (JsonProcessingException e) {
            // 스냅샷을 못 읽는다고 수락을 막지 않는다. 현재 조건으로 진행한다.
            log.warn("MATCHING_DEBUG java.request.accept.snapshot_unreadable snapshotId={} cause={}",
                    snapshot.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * 모집 종료·취소된 프로젝트면 매칭 요청 발송/재추천을 막는다. RECRUITING 이후(협상중·계약대기 등)는
     * 허용한다 — 같은 프로젝트의 다른 포지션이 앞서가도 이 포지션은 여전히 자리가 남아있을 수 있어서다.
     * 모집 종료로 강제 마감된 포지션은 인원이 안 찼어도 CLOSED라 인원 초과 검증만으로는 못 막는다.
     */
    private void assertRecruiting(Long projectId) {
        ProjectStatus status = projectDirectoryPort.findStatus(projectId);
        if (status == ProjectStatus.CANCELED || status == ProjectStatus.CLOSED) {
            throw new BusinessException(MatchingErrorCode.PROJECT_RECRUITING_CLOSED);
        }
    }

    private void syncProjectStage(Long projectId) {
        boolean hasContractPending = matchingRequestRepository.existsByProjectIdAndStatusIn(projectId,
                CONTRACT_PENDING_STATUSES);
        boolean hasNegotiating = matchingRequestRepository.existsByProjectIdAndStatusIn(projectId,
                NEGOTIATING_STATUSES);
        projectCommandUseCase.syncStage(projectId, hasContractPending, hasNegotiating);
    }

    @Override
    @Transactional
    public MatchingRequestResponse reject(Long requestId, String reason, Long accountId) {
        MatchingRequest request = getOwnedByFreelancer(requestId, accountId);
        expireIfOverdue(request);
        request.reject();
        matchingRequestRepository.save(request);
        syncProjectStage(request.getProjectId());
        eventPublisher.publishEvent(MatchingNotificationRequested.rejected(request.getId()));
        return matchingRequestResponseAssembler.build(request, accountId);
    }

    /**
     * 응답 기한(3일)이 이미 지났는데 스케줄러가 아직 안 돈 사이에 수락/거절 시도가 들어오면, 그 자리에서
     * 만료로 정리하고 막는다(정책 P45). 스케줄러(10분 주기)만 믿으면 그 사이 창구가 뚫려 있다.
     *
     * <p>{@code MatchingRequestExpirer}를 거쳐 별도 트랜잭션(REQUIRES_NEW)으로 커밋한다 — 여기서
     * 그냥 저장하면, 곧바로 던지는 {@link MatchingErrorCode#REQUEST_EXPIRED} 예외가 accept()/reject()
     * 자신의 트랜잭션을 롤백시켜서 방금 저장한 만료 처리까지 같이 사라진다.
     */
    private void expireIfOverdue(MatchingRequest request) {
        if (!request.isExpired()) {
            return;
        }
        matchingRequestExpirer.expireNow(request);
        throw new BusinessException(MatchingErrorCode.REQUEST_EXPIRED);
    }

    /**
     * 응답 기한(3일)이 지난 요청을 거절(만료)로 일괄 전이한다(정책 P45, 스케줄러 전용).
     *
     * <p>건별로 {@code MatchingRequestExpirer}의 독립 트랜잭션(REQUIRES_NEW)에서 커밋한다 — 한 건이
     * 실패해도 나머지는 이미 커밋된 채로 남는다. 전체를 하나의 트랜잭션으로 묶으면 한 건의 예외가
     * 나머지 건의 커밋까지 위태롭게 할 수 있어서(JPA 예외는 세션을 rollback-only로 만들 수 있다), 그
     * 위험을 여기서 없앤다.
     */
    @Override
    public int expireOverdueRequests() {
        List<MatchingRequest> overdue = matchingRequestRepository.findExpiredPending(LocalDateTime.now());
        if (overdue.isEmpty()) {
            return 0;
        }

        int expiredCount = 0;
        for (MatchingRequest request : overdue) {
            try {
                matchingRequestExpirer.expireNow(request);
                expiredCount++;
            } catch (Exception e) {
                log.error("[매칭 요청 자동 만료] requestId={} 처리 실패", request.getId(), e);
            }
        }

        log.info("[매칭 요청 자동 만료] 대상 {}건 중 {}건 처리 완료", overdue.size(), expiredCount);
        return expiredCount;
    }


    private MatchingRequest getOwnedByFreelancer(Long requestId, Long accountId) {
        MatchingRequest request = matchingRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.REQUEST_NOT_FOUND));
        Long freelancerId = freelancerDirectoryPort.resolveFreelancerId(accountId);
        if (!request.getFreelancerId().equals(freelancerId)) {
            throw new BusinessException(GlobalErrorCode.ACCESS_DENIED);
        }
        return request;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MatchingRequestResponse> findSentRequests(Long projectId, Long positionId,
                                                                   MatchingStatus status, int page, int size,
                                                                   Long accountId) {
        List<Long> projectIds;
        if (projectId != null) {
            if (!projectDirectoryPort.isOwnedByAccount(projectId, accountId)) {
                throw new BusinessException(GlobalErrorCode.ACCESS_DENIED);
            }
            projectIds = List.of(projectId);
        } else {
            projectIds = projectDirectoryPort.findProjectIdsOwnedByAccount(accountId);
        }

        Page<MatchingRequest> requestPage = matchingRequestRepository.findSentRequests(projectIds, positionId,
                status, PageRequest.of(page, size));
        return toPageResponse(requestPage, accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MatchingRequestResponse> findReceivedRequests(MatchingRequestTab tab, int page, int size,
                                                                       Long accountId) {
        Long freelancerId = freelancerDirectoryPort.resolveFreelancerId(accountId);
        Page<MatchingRequest> requestPage = matchingRequestRepository.findReceivedRequests(freelancerId,
                tab.getStatuses(), PageRequest.of(page, size));
        return toPageResponse(requestPage, accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public MatchingRequestResponse findRequest(Long requestId, Long accountId) {
        MatchingRequest request = matchingRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.REQUEST_NOT_FOUND));

        // 당사자 판별은 **예외가 아니라 값으로** 한다. 이 자리에서 두 번 사고가 났다.
        //   2026-08-09: resolveFreelancerId 를 먼저 불러서 클라이언트가 늘 MT_015(404)
        //   2026-08-13: isOwnedByAccount 를 먼저 불러서 프리랜서가 늘 AC_002(404)
        // 둘 다 "던지는 조회로 신분을 확인"해서 난 문제다. isOwnedByAccount 는 accountId 로
        // client_profile 을 찾는데 프리랜서에겐 그 행이 없고, resolveFreelancerId 는 반대다.
        // 그래서 안 던지는 findFreelancerId 로 먼저 갈라놓고, 각 분기에서만 자기 쪽 조회를 쓴다.
        Optional<Long> callerFreelancerId = freelancerDirectoryPort.findFreelancerId(accountId);
        if (callerFreelancerId.isPresent()) {
            if (!request.getFreelancerId().equals(callerFreelancerId.get())) {
                throw new BusinessException(GlobalErrorCode.ACCESS_DENIED);
            }
        } else if (!projectDirectoryPort.isOwnedByAccount(request.getProjectId(), accountId)) {
            throw new BusinessException(GlobalErrorCode.ACCESS_DENIED);
        }
        return matchingRequestResponseAssembler.buildDetail(request, accountId);
    }

    private PageResponse<MatchingRequestResponse> toPageResponse(Page<MatchingRequest> requestPage, Long accountId) {
        Page<MatchingRequestResponse> mapped = requestPage.map(request ->
                matchingRequestResponseAssembler.build(request, accountId));
        return PageResponse.from(mapped);
    }
}
