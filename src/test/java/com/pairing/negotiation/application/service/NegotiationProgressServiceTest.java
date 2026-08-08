package com.pairing.negotiation.application.service;

import com.pairing.negotiation.application.usecase.NegotiationProgressUseCase;
import com.pairing.negotiation.application.usecase.NegotiationProgressUseCase.NegotiationProgress;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.model.NegotiationMessage;
import com.pairing.negotiation.domain.model.SenderType;
import com.pairing.negotiation.domain.repository.NegotiationMessageRepository;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 매칭 연동 진행조회: requestId → {negotiationId, 라운드, maxRound, 새 제안 수}, 없으면 empty. */
@SpringBootTest
@Transactional
class NegotiationProgressServiceTest {

    @Autowired
    private NegotiationProgressUseCase progressUseCase;
    @Autowired
    private NegotiationRepository negotiationRepository;
    @Autowired
    private NegotiationMessageRepository messageRepository;

    @Test
    @DisplayName("협상이 없으면 empty")
    void emptyWhenNoNegotiation() {
        assertThat(progressUseCase.findProgressByRequestId(7_770_001L)).isEmpty();
    }

    @Test
    @DisplayName("생성 직후(미시작): 라운드 0, 새 제안 0")
    void freshNegotiationHasZeroRound() {
        Long requestId = 7_770_002L;
        negotiationRepository.save(Negotiation.create(requestId, 8300L, 10L, 20L, 5_000_000L,
                List.of(NegotiationCondition.create(ConditionType.AMOUNT, "4000000", "6000000", 0))));

        NegotiationProgress progress = progressUseCase.findProgressByRequestId(requestId).orElseThrow();
        assertThat(progress.currentRound()).isZero();
        assertThat(progress.maxRound()).isEqualTo(15);
        assertThat(progress.newProposalCount()).isZero();
    }

    @Test
    @DisplayName("라운드 진행 + 현재 라운드 제안 수를 새 제안 개수로 반환")
    void reportsCurrentRoundProposalCount() {
        Long requestId = 7_770_003L;
        Negotiation saved = negotiationRepository.save(Negotiation.create(requestId, 8300L, 10L, 20L, 5_000_000L,
                List.of(NegotiationCondition.create(ConditionType.AMOUNT, "4000000", "6000000", 0),
                        NegotiationCondition.create(ConditionType.PERIOD, "3", "6", 1))));
        Long negotiationId = saved.getId();
        Long amountConditionId = saved.getConditions().get(0).getId();
        Long periodConditionId = saved.getConditions().get(1).getId();

        // 라운드 1 진행 + 두 조건에 제안 1건씩(응답 대기 중).
        saved.incrementRound();
        negotiationRepository.save(saved);
        messageRepository.saveAll(List.of(
                NegotiationMessage.proposal(negotiationId, amountConditionId, 1, SenderType.SYSTEM,
                        "월 500만원 제안", "중간값", "5000000"),
                NegotiationMessage.proposal(negotiationId, periodConditionId, 1, SenderType.SYSTEM,
                        "4개월 제안", "중간값", "4")));

        NegotiationProgress progress = progressUseCase.findProgressByRequestId(requestId).orElseThrow();
        assertThat(progress.negotiationId()).isEqualTo(negotiationId);
        assertThat(progress.currentRound()).isEqualTo(1);
        assertThat(progress.newProposalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("null requestId 는 empty")
    void nullRequestIdIsEmpty() {
        assertThat(progressUseCase.findProgressByRequestId(null)).isEqualTo(Optional.empty());
    }
}
