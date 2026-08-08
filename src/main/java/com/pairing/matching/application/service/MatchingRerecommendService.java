package com.pairing.matching.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.ProjectPositionSummary;
import com.pairing.matching.application.usecase.MatchingRerecommendUseCase;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.RecommendationType;
import com.pairing.matching.domain.repository.MatchingRequestRepository;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import com.pairing.matching.exception.MatchingErrorCode;
import com.pairing.matching.presentation.api.response.CandidateListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchingRerecommendService implements MatchingRerecommendUseCase {

    private static final int MAX_PAID_RERECOMMEND = 5;
    private static final long PAID_COST_PER_HEAD = 10_000L;

    private final ProjectDirectoryPort projectDirectoryPort;
    private final MatchingRoundRepository matchingRoundRepository;
    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingRoundCreationService matchingRoundCreationService;
    private final CandidateResponseAssembler candidateResponseAssembler;

    @Override
    @Transactional
    public CandidateListResponse rerecommend(Long positionId, RecommendationType type, Integer quantity,
                                             Long accountId) {
        ProjectPositionSummary position = projectDirectoryPort.findPositionSummary(positionId);
        Long projectId = position.projectId();
        if (!projectDirectoryPort.isOwnedByAccount(projectId, accountId)) {
            throw new BusinessException(GlobalErrorCode.ACCESS_DENIED);
        }

        int recruitCount;
        long costAmount;
        if (type == RecommendationType.FREE) {
            assertFreeAvailable(projectId);
            recruitCount = position.headcount();
            costAmount = 0L;
        } else {
            assertPaidAvailable(projectId);
            recruitCount = quantity;
            costAmount = quantity * PAID_COST_PER_HEAD;
            // 결제 연동 전이라 costAmount는 회차에 기록만 한다. 실제 결제 처리는 payment 도메인이 붙으면 추가한다.
        }

        MatchingRound round = matchingRoundCreationService.createRound(projectId, positionId, type, recruitCount,
                costAmount);
        return candidateResponseAssembler.build(round, accountId);
    }

    private void assertFreeAvailable(Long projectId) {
        boolean alreadyUsed = matchingRoundRepository.countByProjectIdAndRoundType(projectId,
                RecommendationType.FREE) > 0;
        boolean anyRequestSent = matchingRequestRepository.existsByProjectId(projectId);
        boolean stillActive = matchingRequestRepository.existsActiveByProjectId(projectId);
        if (alreadyUsed || !anyRequestSent || stillActive) {
            throw new BusinessException(MatchingErrorCode.RERECOMMEND_NOT_AVAILABLE);
        }
    }

    private void assertPaidAvailable(Long projectId) {
        long paidUsed = matchingRoundRepository.countByProjectIdAndRoundType(projectId, RecommendationType.PAID);
        if (paidUsed >= MAX_PAID_RERECOMMEND) {
            throw new BusinessException(MatchingErrorCode.RERECOMMEND_NOT_AVAILABLE);
        }
    }
}
