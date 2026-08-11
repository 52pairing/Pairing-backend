package com.pairing.negotiation.application.service;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.repository.ClientProfileRepository;
import com.pairing.account.domain.repository.FreelancerProfileRepository;
import com.pairing.global.exception.BusinessException;
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
import com.pairing.negotiation.domain.model.SenderType;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.domain.model.PartyRole;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            // 조건당 제안 1건 + agreed=false(사람이 승인/거절하는 경로를 테스트).
            return context -> {
                var messages = context.conditions().stream()
                        .map(c -> {
                            NegotiationProposalStub.Proposal p =
                                    NegotiationProposalStub.propose(c.clientValue(), c.freelancerValue());
                            // 실제 A2A·stub 과 같이 '클라 대리인'이 낸 제안으로 둔다. 수락 대상은
                            // 상대가 낸 값이므로, 프리랜서 시점 테스트가 성립하려면 이쪽이어야 한다.
                            return new NegotiationProposalPort.AgentMessage(
                                    "CLIENT_AGENT", c.conditionId(), "PROPOSAL",
                                    p.value(), p.content(), p.reason());
                        })
                        .toList();
                var outcomes = context.conditions().stream()
                        .map(c -> {
                            NegotiationProposalStub.Proposal p =
                                    NegotiationProposalStub.propose(c.clientValue(), c.freelancerValue());
                            return new NegotiationProposalPort.ConditionOutcome(
                                    c.conditionId(), p.value(), false);
                        })
                        .toList();
                return new NegotiationProposalPort.A2AResult(messages, outcomes);
            };
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
                BusinessField.IT_CONTENTS_AI, EmployeeCount.SIZE_50_299, "서울 강남구 테헤란로 1")).getId();
        Long freelancerProfileId = freelancerProfileRepository.save(
                FreelancerProfile.create(FREELANCER_ACCOUNT_ID, LocalDate.of(1990, 1, 1))).getId();

        // start_negotiable 은 NOT NULL(primitive 매핑)이라 반드시 채운다.
        // project 는 project 도메인 소유다. 그쪽 엔티티의 NOT NULL 컬럼이 늘면 여기도 채워야 한다.
        jdbcTemplate.update("INSERT INTO project "
                        + "(id, client_id, title, start_negotiable, "
                        + "period_value, period_unit, budget_amount, work_style, work_form, "
                        + "status, payment_status, total_headcount, confirmed_headcount, "
                        + "extension_count, free_rerecommend_used, paid_rerecommend_used) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                PROJECT_ID, clientProfileId, "페어링 웹 리뉴얼", true,
                6, "MONTH", 50_000_000L, "REMOTE", "FULL_TIME",
                "RECRUITING", "DEPOSIT_PAID", 1, 0, 0, 0, 0);

        // 협상의 requestId(100)가 가리키는 매칭 요청 건. 타결/결렬 시 NegotiationLoopService 가
        // MatchingNegotiationOutcomeUseCase 로 이 건의 상태를 갱신하므로, 실제 흐름과 동일하게
        // NEGOTIATING 상태의 요청 행을 만들어 둔다. (없으면 REQUEST_NOT_FOUND 로 타결/결렬이 롤백된다)
        jdbcTemplate.update("INSERT INTO matching_request "
                        + "(id, project_id, position_id, candidate_id, freelancer_id, "
                        + "status, requested_at, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                100L, PROJECT_ID, 10L, 1L, freelancerProfileId, "NEGOTIATING");

        Negotiation negotiation = Negotiation.create(100L, PROJECT_ID, 10L, freelancerProfileId, 5_000_000L, 5_000_000L,
                List.of(NegotiationCondition.create(ConditionType.AMOUNT, "4000000", "6000000", 0)));
        Negotiation saved = negotiationRepository.save(negotiation);
        negotiationId = saved.getId();
        amountConditionId = saved.getConditions().get(0).getId();
    }

    /**
     * 양측 마지노선을 모두 제출한다. 대리인 협상은 <b>두 번째 제출</b>에서 시작되므로,
     * 라운드 1 이후를 검증하는 테스트는 전부 이걸 거쳐야 한다.
     */
    private void startBothSides() {
        // 중간값 제안(500만)이 양측 마지노선 안에 들어오도록 잡는다. 밖이면 수락이 NG_011 로 막히므로
        // '수락이 정상 동작하는' 경로를 검증할 수 없다.
        loopUseCase.start(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "4800000")));
        loopUseCase.start(negotiationId, CLIENT_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "5200000")));
    }

    @Test
    @DisplayName("start: 한쪽만 제출하면 대기 — 라운드도 안 오르고 제안도 안 생긴다")
    void startWaitsForCounterpartFloor() {
        loopUseCase.start(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "5500000")));

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.getTotalRound()).isZero();
        assertThat(reloaded.getConditions().get(0).getFreelancerFloor()).isEqualTo("5500000");
        assertThat(reloaded.getConditions().get(0).getClientFloor()).isNull();

        assertThat(messageRepository.findByNegotiationId(negotiationId))
                .noneMatch(m -> m.getMessageType() == NegotiationMessageType.PROPOSAL);
    }

    @Test
    @DisplayName("start: 해석할 수 없는 마지노선은 거부(NG_004)")
    void startRejectsUnparsableFloorValue() {
        // 화면이 "350만원"·"재택" 처럼 사람이 읽는 표기를 그대로 보내던 사례.
        // 이대로 저장되면 대리인이 다른 값과 비교조차 못 한다.
        assertThatThrownBy(() -> loopUseCase.start(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "550만원"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("start: 마지노선은 계약 표기로 정규화해 저장한다")
    void startNormalizesFloorValue() {
        loopUseCase.start(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "5,500,000")));

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.getConditions().get(0).getFreelancerFloor()).isEqualTo("5500000");
    }

    @Test
    @DisplayName("start: 같은 당사자가 두 번 제출하면 NG_003")
    void startRejectsDuplicateSubmission() {
        loopUseCase.start(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "5500000")));

        assertThatThrownBy(() -> loopUseCase.start(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "5000000"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("마지노선을 이미 제출했습니다");
    }

    @Test
    @DisplayName("start: 마지노선 저장 + 라운드1 초기 제안(중간값) 생성")
    void startGeneratesInitialProposal() {
        startBothSides();

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
        startBothSides();

        loopUseCase.answer(negotiationId, FREELANCER_ACCOUNT_ID, 1,
                List.of(new AnswerInput(amountConditionId, true, null)));

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NegotiationStatus.AGREED);
        assertThat(reloaded.getAgreedAmount()).isEqualTo(5_000_000L);
        assertThat(reloaded.getConditions().get(0).getStatus()).isEqualTo(ConditionStatus.AGREED);
        assertThat(reloaded.getConditions().get(0).getAgreedValue()).isEqualTo("5000000");

        // 타결 시점 최종 조건이 로그에 봉인된다(증거).
        assertThat(messageRepository.findByNegotiationId(negotiationId))
                .anyMatch(m -> m.getContent().contains("최종 조건 봉인")
                        && m.getContent().contains("AMOUNT=5000000"));
    }

    @Test
    @DisplayName("answer 거절(값 없음): REJECTED 로 두고 라운드를 태우지 않는다 — 새 마지노선 대기")
    void rejectWithoutValueWaitsForRedirect() {
        startBothSides();

        // 화면은 [거절]만 누르고 새 마지노선은 그 다음 단계에서 받는다(와이어 6번).
        loopUseCase.answer(negotiationId, FREELANCER_ACCOUNT_ID, 1,
                List.of(new AnswerInput(amountConditionId, false, null)));

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NegotiationStatus.IN_PROGRESS);
        assertThat(reloaded.getConditions().get(0).getStatus()).isEqualTo(ConditionStatus.REJECTED);
        // 같은 마지노선으로 대리인을 다시 돌리면 같은 대화만 반복되므로 라운드를 올리지 않는다.
        assertThat(reloaded.getTotalRound()).isEqualTo(1);
        assertThat(messageRepository.findByNegotiationId(negotiationId))
                .noneMatch(m -> m.getMessageType() == NegotiationMessageType.PROPOSAL && m.getRoundNo() == 2);
    }

    @Test
    @DisplayName("answer 재지시: 거절된 조건에 새 마지노선이 오면 PENDING 복귀 + 라운드 진행")
    void redirectAfterRejectResumesNegotiation() {
        startBothSides();
        loopUseCase.answer(negotiationId, FREELANCER_ACCOUNT_ID, 1,
                List.of(new AnswerInput(amountConditionId, false, null)));

        loopUseCase.answer(negotiationId, FREELANCER_ACCOUNT_ID, 1,
                List.of(new AnswerInput(amountConditionId, false, "5,800,000")));

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.getConditions().get(0).getStatus()).isEqualTo(ConditionStatus.PENDING);
        // 재지시 값도 /start 와 같은 규칙으로 정규화된다.
        assertThat(reloaded.getConditions().get(0).getFreelancerFloor()).isEqualTo("5800000");
        assertThat(reloaded.getTotalRound()).isEqualTo(2);
    }

    @Test
    @DisplayName("answer 수락: 내 마지노선을 벗어난 값은 수락할 수 없다(NG_011)")
    void acceptCannotBreakOwnFloor() {
        startBothSides();   // 프리 하한 5,500,000 / 클라 상한 4,500,000

        // 프리랜서 하한(550만) 아래인 클라 대리인 제안(400만)이 마지막 제안인 상태를 만든다.
        messageRepository.saveAll(List.of(NegotiationMessage.proposal(negotiationId, amountConditionId, 1,
                SenderType.CLIENT_AGENT, "400만원을 제안합니다.", "예산 상한", "4000000")));

        // 사람이 눌렀다고 자기가 그은 선이 무너지면 마지노선을 받은 의미가 없다.
        assertThatThrownBy(() -> loopUseCase.answer(negotiationId, FREELANCER_ACCOUNT_ID, 1,
                List.of(new AnswerInput(amountConditionId, true, null))))
                .isInstanceOf(BusinessException.class);

        assertThat(negotiationRepository.findById(negotiationId).orElseThrow()
                .getConditions().get(0).getStatus()).isEqualTo(ConditionStatus.PENDING);
    }

    @Test
    @DisplayName("answer 수락: 내 편 대리인이 낸 제안은 수락 대상이 아니다(상대 미동의 확정 방지)")
    void acceptIgnoresOwnSideProposal() {
        startBothSides();

        // 프리랜서 대리인이 마지막으로 제안한 상태 — 프리랜서가 자기 요구를 자기가 수락할 수는 없다.
        messageRepository.saveAll(List.of(NegotiationMessage.proposal(negotiationId, amountConditionId, 1,
                SenderType.FREELANCER_AGENT, "600만원을 요청합니다.", "공수 반영", "6000000")));

        loopUseCase.answer(negotiationId, FREELANCER_ACCOUNT_ID, 1,
                List.of(new AnswerInput(amountConditionId, true, null)));

        // 락된 값은 프리 제안(600만)이 아니라 상대(클라) 대리인이 낸 값이어야 한다.
        assertThat(negotiationRepository.findById(negotiationId).orElseThrow()
                .getConditions().get(0).getAgreedValue()).isNotEqualTo("6000000");
    }

    @Test
    @DisplayName("answer 재지시: 해석할 수 없는 새 마지노선은 거부(NG_004)")
    void redirectRejectsUnparsableValue() {
        startBothSides();

        assertThatThrownBy(() -> loopUseCase.answer(negotiationId, FREELANCER_ACCOUNT_ID, 1,
                List.of(new AnswerInput(amountConditionId, false, "580만원"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("answer 거절: 재지시 후 다음 라운드 재제안 생성(라운드 2)")
    void rejectReproposesNextRound() {
        startBothSides();

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
    @DisplayName("실제 협상 로그는 해시 체인이 유효하다(증거 무결성)")
    void logChainIsValid() {
        startBothSides();
        loopUseCase.answer(negotiationId, FREELANCER_ACCOUNT_ID, 1,
                List.of(new AnswerInput(amountConditionId, false, "5800000")));

        var result = com.pairing.negotiation.domain.service.NegotiationLogVerifier.verify(
                messageRepository.findByNegotiationId(negotiationId));

        assertThat(result.valid()).isTrue();
        assertThat(result.checked()).isGreaterThanOrEqualTo(3);   // 제안 + 응답 + 재제안
    }

    @Test
    @DisplayName("give-up: 즉시 결렬(FAILED)")
    void giveUpFails() {
        loopUseCase.giveUp(negotiationId, FREELANCER_ACCOUNT_ID, "예산이 맞지 않습니다.");

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NegotiationStatus.FAILED);
        assertThat(reloaded.getEndReason()).isEqualTo("예산이 맞지 않습니다.");
    }

    @Test
    @DisplayName("markRead: 요청자(프리) 쪽 마지막 읽음만 갱신되고 상대(클라)는 그대로")
    void markReadUpdatesRequesterSideOnly() {
        loopUseCase.markRead(negotiationId, FREELANCER_ACCOUNT_ID);

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.lastReadAt(PartyRole.FREELANCER)).isNotNull();
        assertThat(reloaded.lastReadAt(PartyRole.CLIENT)).isNull();
    }
}
