package com.pairing.matching.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.matching.application.usecase.MatchingNegotiationOutcomeUseCase;
import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.matching.domain.model.MatchingStatus;
import com.pairing.matching.domain.repository.MatchingRequestRepository;
import com.pairing.matching.exception.MatchingErrorCode;
import com.pairing.project.application.usecase.ProjectCommandUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 협상 결과(타결/결렬)를 매칭 요청에 반영한다.
 *
 * <p><b>{@code MatchingRequestService}에서 분리한 이유 — 순환 참조.</b> 매칭은 수락 시 협상을 만들고
 * ({@code MatchingRequestService} → {@code NegotiationPort} → {@code NegotiationCommandService}),
 * 협상은 즉시 타결 시 그 결과를 매칭에 알린다(반대 방향). 두 역할을 한 빈이 다 맡으면
 * {@code matchingRequestService → negotiationAdapter → negotiationCommandService → matchingRequestService}
 * 로 빈 생성이 물려서 컨텍스트가 아예 안 뜬다(2026-08-10, 즉시 타결 배선 넣다가 실제로 겪음).
 *
 * <p>이 클래스는 협상 쪽을 호출하지 않고 자기 저장소와 project 도메인만 쓰므로 고리가 끊긴다.
 * <b>여기에 {@code NegotiationPort} 의존을 추가하면 순환이 다시 생긴다.</b>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class MatchingNegotiationOutcomeService implements MatchingNegotiationOutcomeUseCase {

    private static final List<MatchingStatus> NEGOTIATING_STATUSES =
            List.of(MatchingStatus.ACCEPTED, MatchingStatus.NEGOTIATING);
    private static final List<MatchingStatus> CONTRACT_PENDING_STATUSES =
            List.of(MatchingStatus.CONTRACT_PENDING, MatchingStatus.CONTRACTED, MatchingStatus.IN_PROGRESS,
                    MatchingStatus.COMPLETION_PENDING);

    private final MatchingRequestRepository matchingRequestRepository;
    private final ProjectCommandUseCase projectCommandUseCase;

    @Override
    public void markNegotiationAgreed(Long requestId) {
        MatchingRequest request = getRequest(requestId);
        request.agreeNegotiation();
        matchingRequestRepository.save(request);
    }

    @Override
    public void markNegotiationFailed(Long requestId) {
        MatchingRequest request = getRequest(requestId);
        request.failNegotiation();
        matchingRequestRepository.save(request);
        syncProjectStage(request.getProjectId());
    }

    private MatchingRequest getRequest(Long requestId) {
        return matchingRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.REQUEST_NOT_FOUND));
    }

    private void syncProjectStage(Long projectId) {
        boolean hasContractPending = matchingRequestRepository.existsByProjectIdAndStatusIn(projectId,
                CONTRACT_PENDING_STATUSES);
        boolean hasNegotiating = matchingRequestRepository.existsByProjectIdAndStatusIn(projectId,
                NEGOTIATING_STATUSES);
        projectCommandUseCase.syncStage(projectId, hasContractPending, hasNegotiating);
    }
}
