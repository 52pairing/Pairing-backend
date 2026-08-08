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
        // "확인하지 않은 새 제안 수" = 클라가 마지막으로 협상을 읽은 이후 온 AI 제안 수(안 읽었으면 전체).
        // 매칭 요청 카드는 클라 화면이므로 클라 기준으로 센다.
        int newProposalCount = messageRepository.countUnreadProposals(
                negotiation.getId(), negotiation.getClientLastReadAt());
        return new NegotiationProgress(negotiation.getId(), negotiation.getTotalRound(),
                Negotiation.MAX_ROUND, newProposalCount);
    }
}
