package com.pairing.negotiation.application.service;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.repository.ClientProfileRepository;
import com.pairing.account.domain.repository.FreelancerProfileRepository;
import com.pairing.negotiation.application.port.out.NegotiationProposalPort;
import com.pairing.negotiation.application.usecase.NegotiationLoopUseCase;
import com.pairing.negotiation.application.usecase.NegotiationLoopUseCase.AnswerInput;
import com.pairing.negotiation.application.usecase.NegotiationLoopUseCase.FloorInput;
import com.pairing.negotiation.domain.service.NegotiationProposalStub;
import com.pairing.negotiation.domain.model.ConditionStatus;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.model.NegotiationMessage;
import com.pairing.negotiation.domain.model.NegotiationMessageType;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.domain.repository.NegotiationMessageRepository;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 협상 루프(stub) 검증: 시작→초기 제안, 수락→락·타결, 거절→다음 라운드 재제안, 포기→결렬. */
@SpringBootTest
@Transactional
class NegotiationLoopServiceTest {

    /** 파이썬 HTTP 호출 없이 결정적으로 돌리기 위한 stub 제안 포트(폴백과 동일한 중간값 규칙). */
    @TestConfiguration
    static class StubProposalConfig {
        @Bean
        @Primary
        NegotiationProposalPort stubProposalPort() {
            return context -> context.conditions().stream()
                    .map(c -> {
                        NegotiationProposalStub.Proposal p =
                                NegotiationProposalStub.propose(c.clientValue(), c.freelancerValue());
                        return new NegotiationProposalPort.Proposal(
                                c.conditionId(), p.value(), p.content(), p.reason());
                    })
                    .toList();
        }
    }

    @Autowired
    private NegotiationLoopUseCase loopUseCase;
    @Autowired
    private NegotiationRepository negotiationRepository;
    @Autowired
    private NegotiationMessageRepository messageRepository;
    @Autowired
    private ClientProfileRepository clientProfileRepository;
    @Autowired
    private FreelancerProfileRepository freelancerProfileRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long FREELANCER_ACCOUNT_ID = 910_002L;
    private static final Long CLIENT_ACCOUNT_ID = 910_001L;
    private static final Long PROJECT_ID = 8100L;

    private Long negotiationId;
    private Long amountConditionId;

    @BeforeEach
    void setUp() {
        Long clientProfileId = clientProfileRepository.save(ClientProfile.create(
                CLIENT_ACCOUNT_ID, "삼성전자", "1234567890",
                BusinessField.IT_CONTENTS_AI, EmployeeCount.SIZE_50_299)).getId();
        Long freelancerProfileId = freelancerProfileRepository.save(
                FreelancerProfile.create(FREELANCER_ACCOUNT_ID, LocalDate.of(1990, 1, 1))).getId();

        // start_negotiable 은 NOT NULL(primitive 매핑)이라 반드시 채운다.
        jdbcTemplate.update("INSERT INTO project (id, client_id, title, start_negotiable) VALUES (?, ?, ?, ?)",
                PROJECT_ID, clientProfileId, "페어링 웹 리뉴얼", true);

        Negotiation negotiation = Negotiation.create(100L, PROJECT_ID, 10L, freelancerProfileId, 5_000_000L,
                List.of(NegotiationCondition.create(ConditionType.AMOUNT, "4000000", "6000000", 0)));
        Negotiation saved = negotiationRepository.save(negotiation);
        negotiationId = saved.getId();
        amountConditionId = saved.getConditions().get(0).getId();
    }

    @Test
    @DisplayName("start: 마지노선 저장 + 라운드1 초기 제안(중간값) 생성")
    void startGeneratesInitialProposal() {
        loopUseCase.start(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "5500000")));

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.getTotalRound()).isEqualTo(1);

        List<NegotiationMessage> proposals = messageRepository.findByNegotiationId(negotiationId).stream()
                .filter(m -> m.getMessageType() == NegotiationMessageType.PROPOSAL).toList();
        assertThat(proposals).hasSize(1);
        assertThat(proposals.get(0).getProposedValue()).isEqualTo("5000000");   // (400만+600만)/2
    }

    @Test
    @DisplayName("answer 수락: 제안값으로 락되고 전 조건 합의 시 타결(AGREED)")
    void acceptLocksAndAgrees() {
        loopUseCase.start(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "5500000")));

        loopUseCase.answer(negotiationId, FREELANCER_ACCOUNT_ID, 1,
                List.of(new AnswerInput(amountConditionId, true, null)));

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NegotiationStatus.AGREED);
        assertThat(reloaded.getAgreedAmount()).isEqualTo(5_000_000L);
        assertThat(reloaded.getConditions().get(0).getStatus()).isEqualTo(ConditionStatus.AGREED);
        assertThat(reloaded.getConditions().get(0).getAgreedValue()).isEqualTo("5000000");
    }

    @Test
    @DisplayName("answer 거절: 재지시 후 다음 라운드 재제안 생성(라운드 2)")
    void rejectReproposesNextRound() {
        loopUseCase.start(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "5500000")));

        loopUseCase.answer(negotiationId, FREELANCER_ACCOUNT_ID, 1,
                List.of(new AnswerInput(amountConditionId, false, "5800000")));

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NegotiationStatus.IN_PROGRESS);
        assertThat(reloaded.getTotalRound()).isEqualTo(2);

        boolean hasRound2Proposal = messageRepository.findByNegotiationId(negotiationId).stream()
                .anyMatch(m -> m.getMessageType() == NegotiationMessageType.PROPOSAL && m.getRoundNo() == 2);
        assertThat(hasRound2Proposal).isTrue();
    }

    @Test
    @DisplayName("give-up: 즉시 결렬(FAILED)")
    void giveUpFails() {
        loopUseCase.giveUp(negotiationId, FREELANCER_ACCOUNT_ID, "예산이 맞지 않습니다.");

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NegotiationStatus.FAILED);
        assertThat(reloaded.getEndReason()).isEqualTo("예산이 맞지 않습니다.");
    }
}
