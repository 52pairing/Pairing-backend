package com.pairing.matching.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.matching.application.event.RerecommendRequestedEvent;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.ProjectPositionSummary;
import com.pairing.matching.application.usecase.MatchingRerecommendUseCase;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.RecommendationType;
import com.pairing.matching.domain.repository.MatchingRequestRepository;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import com.pairing.matching.exception.MatchingErrorCode;
import com.pairing.project.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 검증과 회차 생성까지만 하고 바로 돌아온다. 후보 채우기(AI 호출)는
     * {@link RerecommendRequestedEventListener}가 커밋 후 비동기로 처리하고, 완료되면 알림을 보낸다.
     *
     * <p>검증은 동기로 남겨둔다 — 한도 초과(MT_008)·모집 종료(MT_014) 같은 건 버튼을 누른 즉시
     * 알려줘야지, 비동기로 넘겨서 알림으로 실패를 통보하면 쓰기 나쁘다.
     */
    @Override
    @Transactional
    public void rerecommend(Long positionId, RecommendationType type, Integer quantity, Long accountId) {
        if (type != RecommendationType.FREE && type != RecommendationType.PAID) {
            throw new BusinessException(MatchingErrorCode.INVALID_RERECOMMEND_TYPE);
        }
        MatchingRound latestRound = matchingRoundRepository.findLatestByPositionId(positionId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.ROUND_NOT_FOUND));
        Long projectId = latestRound.getProjectId();
        if (!projectDirectoryPort.isOwnedByAccount(projectId, accountId)) {
            throw new BusinessException(GlobalErrorCode.ACCESS_DENIED);
        }
        assertRecruiting(projectId);
        ProjectPositionSummary position = projectDirectoryPort.findPositionSummary(projectId, positionId);

        int recruitCount;
        long costAmount;
        if (type == RecommendationType.FREE) {
            assertFreeAvailable(projectId);
            recruitCount = position.headcount();
            costAmount = 0L;
        } else {
            if (quantity == null) {
                throw new BusinessException(MatchingErrorCode.QUANTITY_REQUIRED);
            }
            assertPaidAvailable(projectId);
            recruitCount = quantity;
            costAmount = quantity * PAID_COST_PER_HEAD;
            // 결제 연동 전이라 costAmount는 회차에 기록만 한다. 실제 결제 처리는 payment 도메인이 붙으면 추가한다.
        }

        // 회차 레코드까지만 동기로 만든다. 저장돼야 한도 검증(assertFreeAvailable/assertPaidAvailable)이
        // 뒤이은 중복 요청을 막을 수 있다. 실제 후보 채우기(AI 호출)는 커밋 후 비동기로 넘긴다.
        MatchingRound round = matchingRoundCreationService.openRound(projectId, positionId, type, recruitCount,
                costAmount);
        eventPublisher.publishEvent(new RerecommendRequestedEvent(round.getId(), accountId));
    }

    /**
     * 모집 종료·취소된 프로젝트면 재추천을 막는다. RECRUITING 이후(협상중·계약대기 등)는 허용한다 —
     * 같은 프로젝트의 다른 포지션이 앞서가도 이 포지션은 여전히 재추천 대상일 수 있어서다.
     */
    private void assertRecruiting(Long projectId) {
        ProjectStatus status = projectDirectoryPort.findStatus(projectId);
        if (status == ProjectStatus.CANCELED || status == ProjectStatus.CLOSED) {
            throw new BusinessException(MatchingErrorCode.PROJECT_RECRUITING_CLOSED);
        }
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
