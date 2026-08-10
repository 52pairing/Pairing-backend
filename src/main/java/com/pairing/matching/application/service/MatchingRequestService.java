package com.pairing.matching.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.freelancer.presentation.api.response.FreelancerConditionResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.exception.BusinessException;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.matching.application.command.CreateNegotiationCommand;
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
import com.pairing.matching.domain.model.MatchingStatus;
import com.pairing.matching.domain.model.SnapshotType;
import com.pairing.matching.domain.model.MatchingSnapshot;
import com.pairing.matching.domain.repository.MatchingCandidateRepository;
import com.pairing.matching.domain.repository.MatchingRequestRepository;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import com.pairing.matching.domain.repository.MatchingSnapshotRepository;
import com.pairing.matching.exception.MatchingErrorCode;
import com.pairing.matching.presentation.api.response.MatchingRequestResponse;
import com.pairing.project.application.usecase.ProjectCommandUseCase;
import com.pairing.project.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MatchingRequestService implements MatchingRequestCommandUseCase, MatchingRequestQueryUseCase,
        MatchingNegotiationOutcomeUseCase {

    private static final List<MatchingStatus> NON_ACTIVE_STATUSES =
            List.of(MatchingStatus.REJECTED, MatchingStatus.NEGOTIATION_FAILED, MatchingStatus.TERMINATED);
    private static final List<MatchingStatus> NEGOTIATING_STATUSES =
            List.of(MatchingStatus.ACCEPTED, MatchingStatus.NEGOTIATING);
    private static final List<MatchingStatus> CONTRACT_PENDING_STATUSES =
            List.of(MatchingStatus.CONTRACT_PENDING, MatchingStatus.CONTRACTED, MatchingStatus.IN_PROGRESS,
                    MatchingStatus.COMPLETION_PENDING);

    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingCandidateRepository matchingCandidateRepository;
    private final MatchingRoundRepository matchingRoundRepository;
    private final MatchingSnapshotRepository matchingSnapshotRepository;
    private final ProjectDirectoryPort projectDirectoryPort;
    private final FreelancerDirectoryPort freelancerDirectoryPort;
    private final NegotiationPort negotiationPort;
    private final ProjectCommandUseCase projectCommandUseCase;
    private final BudgetCapCalculator budgetCapCalculator;
    private final MatchingRequestResponseAssembler matchingRequestResponseAssembler;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public List<MatchingRequestResponse> sendRequests(Long positionId, List<Long> candidateIds, Long accountId) {
        int headcount = projectDirectoryPort.findHeadcount(positionId);
        long activeCount = matchingRequestRepository.countByPositionIdAndStatusNotIn(positionId, NON_ACTIVE_STATUSES);
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
        return matchingRequestResponseAssembler.build(request, accountId);
    }

    @Override
    @Transactional
    public MatchingRequestResponse accept(Long requestId, Long accountId) {
        MatchingRequest request = getOwnedByFreelancer(requestId, accountId);
        request.accept();

        FreelancerConditionResponse condition = freelancerDirectoryPort.findCondition(request.getFreelancerId());
        matchingSnapshotRepository.save(buildFreelancerSnapshot(request, condition));

        ProjectPositionSummary position = projectDirectoryPort.findPositionSummary(request.getProjectId(),
                request.getPositionId());
        long budgetCap = budgetCapCalculator.calculate(request.getProjectId(), position.budgetAmount(),
                position.totalHeadcount(), position.periodValue(), position.periodUnit());

        negotiationPort.createNegotiation(new CreateNegotiationCommand(
                request.getId(), request.getProjectId(), request.getPositionId(), request.getFreelancerId(),
                budgetCap, condition.payUnit(), condition.payAmount(), condition.workStyle(), condition.workForm(),
                condition.availableFrom(), condition.minAcceptAmount(), condition.startNegotiable(),
                condition.periodValue(), condition.periodUnit()));

        request.advanceStatus(MatchingStatus.NEGOTIATING);
        matchingRequestRepository.save(request);
        projectCommandUseCase.startNegotiating(request.getProjectId());
        return matchingRequestResponseAssembler.build(request, accountId);
    }

    private MatchingSnapshot buildFreelancerSnapshot(MatchingRequest request, FreelancerConditionResponse condition) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("payUnit", condition.payUnit());
        payload.put("payAmount", condition.payAmount());
        payload.put("workStyle", condition.workStyle());
        payload.put("workForm", condition.workForm());
        payload.put("availableFrom", condition.availableFrom());
        payload.put("minAcceptAmount", condition.minAcceptAmount());
        payload.put("startNegotiable", condition.startNegotiable());
        // periodValue가 선택 입력이라 null일 수 있다는 전제였으나, freelancer 쪽 DTO가 아직 primitive int라
        // null 구분이 불가능하다(스텁 상태). 실제로 nullable Integer로 바뀌면 아래 두 줄만 조건부 제외로 바꾼다.
        payload.put("periodValue", condition.periodValue());
        payload.put("periodUnit", condition.periodUnit());

        return MatchingSnapshot.create(request.getProjectId(), request.getPositionId(), request.getFreelancerId(),
                SnapshotType.FREELANCER, writeJson(payload));
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

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException(MatchingErrorCode.INVALID_MATCHING_STATE);
        }
    }

    @Override
    @Transactional
    public MatchingRequestResponse reject(Long requestId, String reason, Long accountId) {
        MatchingRequest request = getOwnedByFreelancer(requestId, accountId);
        request.reject();
        matchingRequestRepository.save(request);
        syncProjectStage(request.getProjectId());
        return matchingRequestResponseAssembler.build(request, accountId);
    }

    @Override
    @Transactional
    public void markNegotiationAgreed(Long requestId) {
        MatchingRequest request = matchingRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.REQUEST_NOT_FOUND));
        request.agreeNegotiation();
        matchingRequestRepository.save(request);
    }

    @Override
    @Transactional
    public void markNegotiationFailed(Long requestId) {
        MatchingRequest request = matchingRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.REQUEST_NOT_FOUND));
        request.failNegotiation();
        matchingRequestRepository.save(request);
        syncProjectStage(request.getProjectId());
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

        // 클라이언트 소유 여부를 먼저 본다. resolveFreelancerId를 클라이언트 accountId로 부르면
        // freelancer_profile이 없어 FREELANCER_NOT_FOUND(404)가 나서, 클라이언트가 자기 요청 상세를
        // 볼 때마다 늘 404가 나던 버그가 있었다(테스트로 재현).
        boolean isClientParty = projectDirectoryPort.isOwnedByAccount(request.getProjectId(), accountId);
        if (!isClientParty) {
            Long freelancerId = freelancerDirectoryPort.resolveFreelancerId(accountId);
            if (!request.getFreelancerId().equals(freelancerId)) {
                throw new BusinessException(GlobalErrorCode.ACCESS_DENIED);
            }
        }
        return matchingRequestResponseAssembler.buildDetail(request, accountId);
    }

    private PageResponse<MatchingRequestResponse> toPageResponse(Page<MatchingRequest> requestPage, Long accountId) {
        Page<MatchingRequestResponse> mapped = requestPage.map(request ->
                matchingRequestResponseAssembler.build(request, accountId));
        return PageResponse.from(mapped);
    }
}
