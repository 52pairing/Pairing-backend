package com.pairing.negotiation.application.service;

import com.pairing.negotiation.application.usecase.NegotiationProgressUseCase;
import com.pairing.negotiation.application.usecase.NegotiationProgressUseCase.NegotiationProgress;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.model.NegotiationMessage;
import com.pairing.negotiation.domain.model.PartyRole;
import com.pairing.negotiation.domain.model.SenderType;
import com.pairing.negotiation.domain.repository.NegotiationMessageRepository;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    @DisplayName("클라가 아직 안 읽었으면 새 제안 개수 = 전체 제안 수")
    void unreadIsAllProposalsWhenNeverRead() {
        Long requestId = 7_770_003L;
        Long negotiationId = seedNegotiationWithTwoProposals(requestId);

        NegotiationProgress progress = progressUseCase.findProgressByRequestId(requestId).orElseThrow();
        assertThat(progress.negotiationId()).isEqualTo(negotiationId);
        assertThat(progress.currentRound()).isEqualTo(1);
        assertThat(progress.newProposalCount()).isEqualTo(2);   // 두 제안 다 아직 안 읽음
    }

    @Test
    @DisplayName("클라가 읽은 뒤(이후 새 제안 없음)면 새 제안 개수 = 0")
    void unreadDropsToZeroAfterClientReads() {
        Long requestId = 7_770_004L;
        Long negotiationId = seedNegotiationWithTwoProposals(requestId);

        // 클라가 제안들보다 나중 시점에 읽음 → 그 이후 온 새 제안 없음.
        Negotiation n = negotiationRepository.findById(negotiationId).orElseThrow();
        n.markRead(PartyRole.CLIENT, LocalDateTime.now().plusMinutes(1));
        negotiationRepository.save(n);

        NegotiationProgress progress = progressUseCase.findProgressByRequestId(requestId).orElseThrow();
        assertThat(progress.newProposalCount()).isZero();
    }

    /** 라운드 1 진행 + AMOUNT·PERIOD 제안 1건씩 남긴 협상을 만들고 negotiationId 반환. */
    private Long seedNegotiationWithTwoProposals(Long requestId) {
        Negotiation saved = negotiationRepository.save(Negotiation.create(requestId, 8300L, 10L, 20L, 5_000_000L,
                List.of(NegotiationCondition.create(ConditionType.AMOUNT, "4000000", "6000000", 0),
                        NegotiationCondition.create(ConditionType.PERIOD, "3", "6", 1))));
        Long negotiationId = saved.getId();
        saved.incrementRound();
        negotiationRepository.save(saved);
        messageRepository.saveAll(List.of(
                NegotiationMessage.proposal(negotiationId, saved.getConditions().get(0).getId(), 1,
                        SenderType.SYSTEM, "월 500만원 제안", "중간값", "5000000"),
                NegotiationMessage.proposal(negotiationId, saved.getConditions().get(1).getId(), 1,
                        SenderType.SYSTEM, "4개월 제안", "중간값", "4")));
        return negotiationId;
    }

    @Test
    @DisplayName("null requestId 는 empty")
    void nullRequestIdIsEmpty() {
        assertThat(progressUseCase.findProgressByRequestId(null)).isEqualTo(Optional.empty());
    }
}
