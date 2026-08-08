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
public class MatchingRequestService implements MatchingRequestCommandUseCase, MatchingRequestQueryUseCase {

    private static final List<MatchingStatus> NON_ACTIVE_STATUSES =
            List.of(MatchingStatus.REJECTED, MatchingStatus.NEGOTIATION_FAILED, MatchingStatus.TERMINATED);

    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingCandidateRepository matchingCandidateRepository;
    private final MatchingRoundRepository matchingRoundRepository;
    private final MatchingSnapshotRepository matchingSnapshotRepository;
    private final ProjectDirectoryPort projectDirectoryPort;
    private final FreelancerDirectoryPort freelancerDirectoryPort;
    private final NegotiationPort negotiationPort;
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
                position.headcount());

        negotiationPort.createNegotiation(new CreateNegotiationCommand(
                request.getId(), request.getProjectId(), request.getPositionId(), request.getFreelancerId(),
                budgetCap, condition.payUnit(), condition.payAmount(), condition.workStyle(), condition.workForm(),
                condition.availableFrom(), condition.minAcceptAmount(), condition.startNegotiable(),
                condition.periodValue(), condition.periodUnit()));

        request.advanceStatus(MatchingStatus.NEGOTIATING);
        matchingRequestRepository.save(request);
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
        return matchingRequestResponseAssembler.build(request, accountId);
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

        Long freelancerId = freelancerDirectoryPort.resolveFreelancerId(accountId);
        boolean isFreelancerParty = request.getFreelancerId().equals(freelancerId);
        boolean isClientParty = projectDirectoryPort.isOwnedByAccount(request.getProjectId(), accountId);
        if (!isFreelancerParty && !isClientParty) {
            throw new BusinessException(GlobalErrorCode.ACCESS_DENIED);
        }
        return matchingRequestResponseAssembler.build(request, accountId);
    }

    private PageResponse<MatchingRequestResponse> toPageResponse(Page<MatchingRequest> requestPage, Long accountId) {
        Page<MatchingRequestResponse> mapped = requestPage.map(request ->
                matchingRequestResponseAssembler.build(request, accountId));
        return PageResponse.from(mapped);
    }
}
