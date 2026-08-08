package com.pairing.negotiation.application.service;

import com.pairing.negotiation.application.usecase.NegotiationProgressUseCase;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.repository.NegotiationMessageRepository;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 매칭 도메인이 소비하는 협상 진행조회. requestId → 협상 요약(라운드·제안 수).
 * 화면 조회와 분리해 "매칭↔협상 통합"의 관심사를 한곳에 둔다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NegotiationProgressService implements NegotiationProgressUseCase {

    private final NegotiationRepository negotiationRepository;
    private final NegotiationMessageRepository messageRepository;

    @Override
    public Optional<NegotiationProgress> findProgressByRequestId(Long requestId) {
        if (requestId == null) {
            return Optional.empty();
        }
        return negotiationRepository.findByRequestId(requestId).map(this::toProgress);
    }

    private NegotiationProgress toProgress(Negotiation negotiation) {
        int currentRound = negotiation.getTotalRound();
        int newProposalCount = currentRound > 0
                ? messageRepository.countProposalsInRound(negotiation.getId(), currentRound)
                : 0;
        return new NegotiationProgress(negotiation.getId(), currentRound, Negotiation.MAX_ROUND, newProposalCount);
    }
}
