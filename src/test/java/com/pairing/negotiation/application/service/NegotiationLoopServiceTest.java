package com.pairing.negotiation.application.service;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.repository.ClientProfileRepository;
import com.pairing.account.domain.repository.FreelancerProfileRepository;
import com.pairing.global.exception.BusinessException;
import com.pairing.negotiation.application.event.NegotiationEvent.NegotiationEventType;
import com.pairing.negotiation.application.port.out.FreelancerConditionReaderPort;
import com.pairing.negotiation.application.port.out.NegotiationProposalPort;
import com.pairing.negotiation.application.usecase.NegotiationAgentUseCase;
import com.pairing.negotiation.application.usecase.NegotiationLoopUseCase;
import com.pairing.negotiation.application.usecase.NegotiationLoopUseCase.AnswerInput;
import com.pairing.negotiation.application.usecase.NegotiationLoopUseCase.FloorInput;
import com.pairing.negotiation.domain.service.NegotiationProposalStub;
import com.pairing.negotiation.domain.model.ConditionStatus;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationAgentState;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 협상 루프(stub) 검증: 시작→초기 제안, 수락→락·타결, 거절→다음 라운드 재제안, 포기→결렬. */
@SpringBootTest
@Transactional
class NegotiationLoopServiceTest {

    /** 대리인끼리 합의한 것으로 응답할지(outcome.agreed). 기본은 false — 사람이 승인/거절하는 경로. */
    private static boolean agreeOnPropose = false;

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
                                    c.conditionId(), p.value(), agreeOnPropose);
                        })
                        .toList();
                return new NegotiationProposalPort.A2AResult(messages, outcomes);
            };
        }
    }

    @Autowired
    private NegotiationLoopUseCase loopUseCase;
    @Autowired
    private NegotiationAgentUseCase agentUseCase;
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

    // 프리 등록 최소 수용가 조회 포트. 실제 어댑터는 freelancer_condition(enum NOT NULL 다수)을 읽으므로
    // 테스트에선 목으로 값을 주입한다. 스텁 안 하면 기본이 Optional.empty() 라 기존 테스트엔 영향 없다.
    @MockBean
    private FreelancerConditionReaderPort freelancerConditionReaderPort;

    private static final Long FREELANCER_ACCOUNT_ID = 910_002L;
    private static final Long CLIENT_ACCOUNT_ID = 910_001L;
    private static final Long PROJECT_ID = 8100L;

    private Long negotiationId;
    private Long amountConditionId;
    private Long freelancerProfileId;

    @BeforeEach
    void setUp() {
        agreeOnPropose = false;   // 테스트 간 누수 방지(스텁이 정적 플래그를 읽는다)
        Long clientProfileId = clientProfileRepository.save(ClientProfile.create(
                CLIENT_ACCOUNT_ID, "삼성전자", "1234567890",
                BusinessField.IT_CONTENTS_AI, EmployeeCount.SIZE_50_299, "서울 강남구 테헤란로 1")).getId();
        freelancerProfileId = freelancerProfileRepository.save(
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
     * 양측 마지노선을 모두 제출하고 <b>대리인까지 돌린다</b>(= 라운드 1 제안이 있는 상태).
     * 대리인 협상은 두 번째 제출에서 예약되므로, 라운드 1 이후를 검증하는 테스트는 전부 이걸 거친다.
     */
    private void startBothSides() {
        // 중간값 제안(500만)이 양측 마지노선 안에 들어오도록 잡는다. 밖이면 수락이 NG_011 로 막히므로
        // '수락이 정상 동작하는' 경로를 검증할 수 없다.
        loopUseCase.start(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "4800000")));
        loopUseCase.start(negotiationId, CLIENT_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "5200000")));
        runAgent(NegotiationEventType.STARTED);
    }

    /**
     * 커밋 후 리스너가 할 일을 손으로 돌린다.
     *
     * <p>A2A 호출이 비동기라 제안 생성은 {@code NegotiationAgentListener} 가
     * {@code AFTER_COMMIT} 에서 한다. <b>이 테스트 클래스는 {@code @Transactional} 이라 커밋이
     * 없고, 따라서 그 리스너가 뜨지 않는다.</b> 그래서 대리인 단계만 직접 부른다 — 검증 대상은
     * 리스너의 배선이 아니라 그 안에서 벌어지는 협상 로직이다.
     * (배선 자체는 {@code scheduleAgent} 가 상태를 RUNNING 으로 바꾸는지로 따로 확인한다.)
     */
    private void runAgent(NegotiationEventType type) {
        agentUseCase.runAgent(negotiationId, type);
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
        runAgent(NegotiationEventType.ANSWERED);

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.getConditions().get(0).getStatus()).isEqualTo(ConditionStatus.PENDING);
        // 재지시 값도 /start 와 같은 규칙으로 정규화된다.
        assertThat(reloaded.getConditions().get(0).getFreelancerFloor()).isEqualTo("5800000");
        assertThat(reloaded.getTotalRound()).isEqualTo(2);
    }

    @Test
    @DisplayName("조건 라운드 수는 대리인이 그 쟁점을 논의할 때마다 오른다(재지시로는 안 오른다)")
    void conditionRoundCountFollowsAgentDiscussion() {
        startBothSides();   // 라운드 1 — 대리인이 AMOUNT 를 논의했다

        assertThat(conditionRoundCount()).isEqualTo(1);

        // [거절]만 눌린 상태는 아직 오간 말이 없다. 여기서 오르면 재지시 한 번이 두 라운드로 세진다.
        loopUseCase.answer(negotiationId, FREELANCER_ACCOUNT_ID, 1,
                List.of(new AnswerInput(amountConditionId, false, null)));
        assertThat(conditionRoundCount()).isEqualTo(1);

        // 재지시 → 대리인이 라운드 2 를 돌면 그때 오른다.
        loopUseCase.answer(negotiationId, FREELANCER_ACCOUNT_ID, 1,
                List.of(new AnswerInput(amountConditionId, false, "5,800,000")));
        runAgent(NegotiationEventType.ANSWERED);

        assertThat(conditionRoundCount()).isEqualTo(2);
    }

    private int conditionRoundCount() {
        return negotiationRepository.findById(negotiationId).orElseThrow()
                .getConditions().get(0).getRoundCount();
    }

    @Test
    @DisplayName("대리인끼리 전 조건을 합의하면 사람 응답 없이도 타결된다(AGREED)")
    void agentAgreementSettlesWithoutHumanAnswer() {
        // 대리인이 합의(agreed=true)를 내놓는 포트로 바꿔 끼운다.
        agreeOnPropose = true;

        startBothSides();

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        // 예전에는 조건만 전부 🔒 되고 협상은 IN_PROGRESS 에 갇혔다 — 계약서도 안 생겼다.
        assertThat(reloaded.getStatus()).isEqualTo(NegotiationStatus.AGREED);
        assertThat(reloaded.getConditions().get(0).getStatus()).isEqualTo(ConditionStatus.AGREED);
        assertThat(messageRepository.findByNegotiationId(negotiationId))
                .anyMatch(m -> m.getContent().contains("최종 조건 봉인"));
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
    @DisplayName("answer 수락: acceptBelowFloor=true 면 내 마지노선 아래 제안도 직접 수락된다")
    void acceptBelowOwnFloorWhenExplicitlyConfirmed() {
        startBothSides();   // 프리 하한 4,800,000 / 클라 상한 5,200,000

        // 프리 하한(480만) 아래인 클라 대리인 제안(400만)이 마지막 제안인 상태.
        messageRepository.saveAll(List.of(NegotiationMessage.proposal(negotiationId, amountConditionId, 1,
                SenderType.CLIENT_AGENT, "400만원을 제안합니다.", "예산 상한", "4000000")));

        // 사람이 "내 선을 넘겨서라도 받겠다"고 명시(acceptBelowFloor=true) → 내 하한 검증만 건너뛰고 수락된다.
        loopUseCase.answer(negotiationId, FREELANCER_ACCOUNT_ID, 1,
                List.of(new AnswerInput(amountConditionId, true, null, true)));

        assertThat(negotiationRepository.findById(negotiationId).orElseThrow()
                .getConditions().get(0).getAgreedValue()).isEqualTo("4000000");
    }

    @Test
    @DisplayName("answer 수락: acceptBelowFloor 여도 상대 마지노선은 못 넘는다")
    void acceptBelowFloorStillRespectsOpponentFloor() {
        startBothSides();   // 클라 상한 5,200,000

        // 클라 상한(520만)을 넘는 값이 마지막 제안인 (비정상) 상태. acceptBelowFloor 여도 상대 선은 지켜야 한다.
        messageRepository.saveAll(List.of(NegotiationMessage.proposal(negotiationId, amountConditionId, 1,
                SenderType.CLIENT_AGENT, "600만원을 제안합니다.", "테스트", "6000000")));

        assertThatThrownBy(() -> loopUseCase.answer(negotiationId, FREELANCER_ACCOUNT_ID, 1,
                List.of(new AnswerInput(amountConditionId, true, null, true))))
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
    @DisplayName("start: 등록 최소가보다 낮은 마지노선은 플래그 없으면 막힌다(NG_012)")
    void startBelowMinAcceptBlockedWithoutFlag() {
        // 프리 등록 최소 수용가 = 440만. 그보다 낮은 420만을 처음 마지노선으로 제출.
        when(freelancerConditionReaderPort.findMinAcceptAmount(any())).thenReturn(Optional.of(4_400_000L));

        assertThatThrownBy(() -> loopUseCase.start(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "4200000"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("최소 수용");
    }

    @Test
    @DisplayName("start: belowMinAccept=true 면 등록 최소가보다 낮아도 허용(경고 후 확인 경로)")
    void startBelowMinAcceptAllowedWithFlag() {
        when(freelancerConditionReaderPort.findMinAcceptAmount(any())).thenReturn(Optional.of(4_400_000L));

        // 사람이 "등록 최소가보다 낮지만 그래도 이 선으로 하겠다"고 확인 → 그대로 저장된다.
        loopUseCase.start(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "4200000", true)));

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.getConditions().get(0).getFreelancerFloor()).isEqualTo("4200000");
    }

    @Test
    @DisplayName("updateFloors: 재조정은 belowMinAccept 여부와 무관하게 등록 최소가 하한을 강제한다")
    void updateFloorsAlwaysEnforcesMinAccept() {
        startBothSides();   // 최소가 스텁 전이라 통과(기본 empty). 이제 라운드1 상태.
        when(freelancerConditionReaderPort.findMinAcceptAmount(any())).thenReturn(Optional.of(4_400_000L));

        // 경고-후-허용은 최초 제출(start)에만 열었다. 재조정에선 플래그를 줘도 등록 최소가 아래는 막힌다.
        assertThatThrownBy(() -> loopUseCase.updateFloors(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "4200000", true))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("최소 수용");
    }

    @Test
    @DisplayName("마지노선 재설정: 값만 갱신되고 라운드는 오르지 않는다")
    void updateFloorsDoesNotAdvanceRound() {
        startBothSides();   // 프리 하한 4,800,000 / 라운드 1
        int roundBefore = negotiationRepository.findById(negotiationId).orElseThrow().getTotalRound();

        loopUseCase.updateFloors(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "4,200,000")));

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.getConditions().get(0).getFreelancerFloor()).isEqualTo("4200000");   // 정규화까지
        // 조정만으로는 대리인이 돌지 않는다 — 라운드 상한 우회에 쓸 수 없다.
        assertThat(reloaded.getTotalRound()).isEqualTo(roundBefore);
        assertThat(messageRepository.findByNegotiationId(negotiationId))
                .noneMatch(m -> m.getMessageType() == NegotiationMessageType.PROPOSAL
                        && m.getRoundNo() > roundBefore);
    }

    @Test
    @DisplayName("마지노선 재설정: 조정하면 막혔던 수락이 통과한다")
    void updateFloorsUnblocksAccept() {
        startBothSides();

        // 프리랜서 하한(480만) 아래인 클라 제안(400만) → 지금은 수락이 막힌다.
        messageRepository.saveAll(List.of(NegotiationMessage.proposal(negotiationId, amountConditionId, 1,
                SenderType.CLIENT_AGENT, "400만원을 제안합니다.", "예산 상한", "4000000")));
        assertThatThrownBy(() -> loopUseCase.answer(negotiationId, FREELANCER_ACCOUNT_ID, 1,
                List.of(new AnswerInput(amountConditionId, true, null))))
                .isInstanceOf(BusinessException.class);

        // 선을 넓히면(480만 → 390만) 같은 제안이 수락된다. 오류 문구가 안내하는 그 경로다.
        loopUseCase.updateFloors(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "3900000")));
        loopUseCase.answer(negotiationId, FREELANCER_ACCOUNT_ID, 1,
                List.of(new AnswerInput(amountConditionId, true, null)));

        assertThat(negotiationRepository.findById(negotiationId).orElseThrow()
                .getConditions().get(0).getAgreedValue()).isEqualTo("4000000");
    }

    @Test
    @DisplayName("마지노선 재설정: 이미 합의된 쟁점은 고칠 수 없다(NG_006)")
    void updateFloorsRejectsAgreedCondition() {
        agreeOnPropose = true;
        startBothSides();   // 대리인 합의로 AMOUNT 가 락된다

        assertThatThrownBy(() -> loopUseCase.updateFloors(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "4200000"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("마지노선 재설정: 해석할 수 없는 값은 거부(NG_004)")
    void updateFloorsRejectsUnparsableValue() {
        startBothSides();

        assertThatThrownBy(() -> loopUseCase.updateFloors(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "420만원"))))
                .isInstanceOf(BusinessException.class);
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
        runAgent(NegotiationEventType.ANSWERED);

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
        runAgent(NegotiationEventType.ANSWERED);

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
    @DisplayName("give-up: 대리인이 도는 중에 포기하면 예약이 정리돼 뒤늦은 A2A 응답이 버려진다")
    void giveUpWhileAgentRunningDiscardsLateResult() {
        // 양측 제출로 대리인이 예약된 상태(RUNNING). A2A 응답은 아직 안 왔다.
        loopUseCase.start(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "4800000")));
        loopUseCase.start(negotiationId, CLIENT_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "5200000")));
        assertThat(negotiationRepository.findById(negotiationId).orElseThrow().getAgentState())
                .isEqualTo(NegotiationAgentState.RUNNING);

        loopUseCase.giveUp(negotiationId, FREELANCER_ACCOUNT_ID, "더 기다릴 수 없습니다.");

        Negotiation afterGiveUp = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(afterGiveUp.getStatus()).isEqualTo(NegotiationStatus.FAILED);
        assertThat(afterGiveUp.getAgentState()).isEqualTo(NegotiationAgentState.IDLE);
        assertThat(afterGiveUp.getAgentStartedAt()).isNull();

        // 15초 뒤 A2A 응답이 도착한 상황. 예약이 없으므로 조용히 빠져야 한다.
        // 예약을 안 지우면 여기서 NOT_IN_PROGRESS 가 터지고, 리스너가 그걸 대리인 실패로 오해해
        // 이미 끝난 협상에 "다시 시도해 주세요" 안내를 붙인다(실측으로 확인한 경로).
        runAgent(NegotiationEventType.STARTED);

        Negotiation afterLateRun = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(afterLateRun.getStatus()).isEqualTo(NegotiationStatus.FAILED);
        assertThat(afterLateRun.getTotalRound()).isZero();
        assertThat(messageRepository.findByNegotiationId(negotiationId))
                .noneMatch(m -> m.getMessageType() == NegotiationMessageType.PROPOSAL);
    }

    // ----- 대리인(A2A) 비동기 실행 -----

    @Test
    @DisplayName("양측 제출 시점엔 대리인 예약만 된다 — 제안은 아직 없다(요청 스레드가 A2A 를 안 기다린다)")
    void bothSidesSubmittedOnlySchedulesAgent() {
        loopUseCase.start(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "4800000")));
        loopUseCase.start(negotiationId, CLIENT_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "5200000")));

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.getAgentState()).isEqualTo(NegotiationAgentState.RUNNING);
        assertThat(reloaded.getAgentStartedAt()).isNotNull();
        // 라운드도 제안도 대리인이 실제로 돈 뒤에 생긴다.
        assertThat(reloaded.getTotalRound()).isZero();
        assertThat(messageRepository.findByNegotiationId(negotiationId))
                .noneMatch(m -> m.getMessageType() == NegotiationMessageType.PROPOSAL);
    }

    @Test
    @DisplayName("대리인이 끝나면 IDLE 로 돌아오고 라운드·제안이 생긴다")
    void agentRunProducesProposalAndReturnsToIdle() {
        startBothSides();   // 예약 + 대리인 실행까지

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.getAgentState()).isEqualTo(NegotiationAgentState.IDLE);
        assertThat(reloaded.getAgentStartedAt()).isNull();
        assertThat(reloaded.getTotalRound()).isEqualTo(1);
        assertThat(messageRepository.findByNegotiationId(negotiationId))
                .anyMatch(m -> m.getMessageType() == NegotiationMessageType.PROPOSAL);
    }

    @Test
    @DisplayName("이미 도는 중이면 대리인을 두 번 돌리지 않는다(제안 중복·A2A 비용 두 배 방지)")
    void agentDoesNotRunTwiceConcurrently() {
        loopUseCase.start(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "4800000")));
        loopUseCase.start(negotiationId, CLIENT_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "5200000")));   // → RUNNING

        // 첫 실행이 라운드 1 을 만들고 IDLE 로 돌아온다.
        runAgent(NegotiationEventType.STARTED);
        // 예약이 이미 정리됐으므로 같은 신호가 또 와도 아무 일도 없어야 한다
        // (리스너 재전달·사용자 더블클릭에 해당).
        runAgent(NegotiationEventType.STARTED);

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.getTotalRound()).isEqualTo(1);
        assertThat(messageRepository.findByNegotiationId(negotiationId).stream()
                .filter(m -> m.getMessageType() == NegotiationMessageType.PROPOSAL)
                .toList()).hasSize(1);
    }

    @Test
    @DisplayName("대리인 실패는 FAILED 로 남고 안내 메시지가 붙는다 — 조용히 사라지지 않는다")
    void agentFailureIsRecorded() {
        loopUseCase.start(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "4800000")));
        loopUseCase.start(negotiationId, CLIENT_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "5200000")));   // → RUNNING

        // 리스너가 runAgent 실패를 잡고 부르는 경로.
        agentUseCase.markAgentFailed(negotiationId);

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.getAgentState()).isEqualTo(NegotiationAgentState.FAILED);
        // 라운드는 오르지 않았다 — 실패한 호출로 라운드를 태우면 상한이 억울하게 깎인다.
        assertThat(reloaded.getTotalRound()).isZero();
        assertThat(messageRepository.findByNegotiationId(negotiationId))
                .anyMatch(m -> m.getContent().contains("다시 시도해 주세요"));
    }

    @Test
    @DisplayName("실패한 뒤에도 다시 예약할 수 있다(재시도 경로)")
    void failedAgentCanBeRetried() {
        loopUseCase.start(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "4800000")));
        loopUseCase.start(negotiationId, CLIENT_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "5200000")));
        agentUseCase.markAgentFailed(negotiationId);

        // 재지시(마지노선 조정 후 응답)가 다시 대리인을 예약한다.
        loopUseCase.updateFloors(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "4700000")));
        Negotiation beforeRetry = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(beforeRetry.getAgentState()).isEqualTo(NegotiationAgentState.FAILED);

        assertThat(beforeRetry.beginAgentRun()).isTrue();   // FAILED → RUNNING 이 막히지 않는다
    }

    @Test
    @DisplayName("markRead: 요청자(프리) 쪽 마지막 읽음만 갱신되고 상대(클라)는 그대로")
    void markReadUpdatesRequesterSideOnly() {
        loopUseCase.markRead(negotiationId, FREELANCER_ACCOUNT_ID);

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.lastReadAt(PartyRole.FREELANCER)).isNotNull();
        assertThat(reloaded.lastReadAt(PartyRole.CLIENT)).isNull();
    }

    // ----- 최종 절충안(Final Compromise Offer) -----

    /**
     * 라운드 상한(15회)까지 몰아 최종 절충 단계로 진입시킨다.
     *
     * <p>정책상 <b>15라운드까지는 무엇을 하든 협상 기회를 준다</b> — 조기 트리거는 없다. 그래서
     * 사람이 계속 재지시(거절+새 마지노선)하며 15라운드를 소진하고, 그 시점에 최종 절충안이 제시된다.
     * 프리 하한 600만 · 클라 상한 300만(끝내 안 겹침)이라 절충값 = (300만+600만)/2 = 450만.
     */
    private void startAndReachFinalOffer() {
        loopUseCase.start(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "6000000")));
        loopUseCase.start(negotiationId, CLIENT_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "3000000")));
        runAgent(NegotiationEventType.STARTED);   // 라운드 1
        driveRedirectsUntilFinalOfferOrFail(negotiationId, amountConditionId);
    }

    /** 사람이 계속 재지시하며 라운드를 태워, 최종 절충 단계(또는 결렬)에 도달할 때까지 대리인을 돌린다. */
    private void driveRedirectsUntilFinalOfferOrFail(Long negId, Long conditionId) {
        for (int i = 0; i < Negotiation.MAX_ROUND + 2; i++) {
            Negotiation n = negotiationRepository.findById(negId).orElseThrow();
            if (n.isFinalOffer() || n.getStatus() != NegotiationStatus.IN_PROGRESS) {
                return;
            }
            // 거절+새 마지노선(같은 값) → 다음 라운드로. 대리인은 끝내 합의 못 한다(스텁 agreed=false).
            loopUseCase.answer(negId, FREELANCER_ACCOUNT_ID, n.getTotalRound(),
                    List.of(new AnswerInput(conditionId, false, "6000000")));
            agentUseCase.runAgent(negId, NegotiationEventType.ANSWERED);
        }
    }

    @Test
    @DisplayName("15라운드까지 합의 못 하면 그때 최종 절충안 제시(결렬시키지 않고 중재)")
    void roundLimitEntersFinalOffer() {
        startAndReachFinalOffer();

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.isFinalOffer()).isTrue();
        assertThat(reloaded.getStatus()).isEqualTo(NegotiationStatus.IN_PROGRESS);   // 결렬이 아니다
        // 15라운드를 다 쓰고 나서야 넘어간다 — 그 전까지는 협상 기회를 준다.
        assertThat(reloaded.getTotalRound()).isEqualTo(Negotiation.MAX_ROUND);
        // 절충값이 미합의 조건에 붙지만 아직 락되진 않는다(양측 수락 대기).
        NegotiationCondition amount = reloaded.getConditions().get(0);
        assertThat(amount.getCompromiseValue()).isEqualTo("4500000");
        assertThat(amount.getStatus()).isEqualTo(ConditionStatus.PENDING);
        assertThat(messageRepository.findByNegotiationId(negotiationId))
                .anyMatch(m -> m.getContent().contains("최종 절충안을 제시"));
    }

    @Test
    @DisplayName("최종 절충안: 한쪽만 수락하면 상대 응답을 기다린다(아직 타결 아님)")
    void finalOfferOneSideAcceptWaits() {
        startAndReachFinalOffer();

        loopUseCase.acceptFinalOffer(negotiationId, FREELANCER_ACCOUNT_ID);

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NegotiationStatus.IN_PROGRESS);
        assertThat(reloaded.isFinalOffer()).isTrue();
        assertThat(reloaded.hasAcceptedFinalOffer(PartyRole.FREELANCER)).isTrue();
        assertThat(reloaded.hasAcceptedFinalOffer(PartyRole.CLIENT)).isFalse();
        assertThat(reloaded.getConditions().get(0).getStatus()).isEqualTo(ConditionStatus.PENDING);
    }

    @Test
    @DisplayName("최종 절충안: 양측이 모두 수락하면 절충값으로 락되고 타결(AGREED)")
    void finalOfferBothAcceptSettles() {
        startAndReachFinalOffer();

        loopUseCase.acceptFinalOffer(negotiationId, FREELANCER_ACCOUNT_ID);
        loopUseCase.acceptFinalOffer(negotiationId, CLIENT_ACCOUNT_ID);

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NegotiationStatus.AGREED);
        // 절충값(450만)이 합의값·합의금액으로 확정된다 — 각자 마지노선을 넘겨 만난 값이다.
        assertThat(reloaded.getConditions().get(0).getAgreedValue()).isEqualTo("4500000");
        assertThat(reloaded.getAgreedAmount()).isEqualTo(4_500_000L);
        assertThat(messageRepository.findByNegotiationId(negotiationId))
                .anyMatch(m -> m.getContent().contains("최종 조건 봉인") && m.getContent().contains("AMOUNT=4500000"));
    }

    @Test
    @DisplayName("최종 절충안: 상대가 포기하면 결렬된다(한쪽 수락 후 상대 거절 = 즉시 결렬)")
    void finalOfferGiveUpFails() {
        startAndReachFinalOffer();
        loopUseCase.acceptFinalOffer(negotiationId, FREELANCER_ACCOUNT_ID);   // 한쪽 수락

        loopUseCase.giveUp(negotiationId, CLIENT_ACCOUNT_ID, "절충안을 받아들일 수 없습니다.");

        Negotiation reloaded = negotiationRepository.findById(negotiationId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NegotiationStatus.FAILED);
    }

    @Test
    @DisplayName("최종 절충 단계에서는 조건별 응답(/answers)이 막힌다 — 절충안 수락/포기만 가능(NG_013)")
    void answerBlockedDuringFinalOffer() {
        startAndReachFinalOffer();

        assertThatThrownBy(() -> loopUseCase.answer(negotiationId, FREELANCER_ACCOUNT_ID,
                Negotiation.MAX_ROUND, List.of(new AnswerInput(amountConditionId, true, null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("최종 절충");
    }

    @Test
    @DisplayName("최종 절충 단계가 아닌데 수락하면 NG_014")
    void acceptFinalOfferRejectedWhenNotInFinalOffer() {
        startBothSides();   // 라운드1 정상 협상 — 최종 절충 단계가 아니다

        assertThatThrownBy(() -> loopUseCase.acceptFinalOffer(negotiationId, FREELANCER_ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("최종 절충 단계가 아닙니다");
    }

    @Test
    @DisplayName("절충 불가 조건(자유 텍스트)이 남으면 최종 절충안을 내지 않고 즉시 결렬")
    void uncompromisableConditionFailsAtRoundLimit() {
        // AMOUNT(라운드 소진까지 미합의) + OTHER(자유 텍스트 → 절충 불가)를 가진 협상.
        Long id = negotiationRepository.save(Negotiation.create(100L, PROJECT_ID, 10L, freelancerProfileId,
                5_000_000L, 5_000_000L, List.of(
                        NegotiationCondition.create(ConditionType.AMOUNT, "4000000", "6000000", 0),
                        NegotiationCondition.create(ConditionType.OTHER, "A 안", "B 안", 1)))).getId();
        Long amountId = negotiationRepository.findById(id).orElseThrow().getConditions().stream()
                .filter(c -> c.getConditionType() == ConditionType.AMOUNT).findFirst().orElseThrow().getId();

        // 양측 마지노선 제출: AMOUNT 는 끝내 안 겹치게, OTHER 는 자유 텍스트로.
        loopUseCase.start(id, FREELANCER_ACCOUNT_ID, List.of(
                new FloorInput(ConditionType.AMOUNT, "6000000"),
                new FloorInput(ConditionType.OTHER, "B 안")));
        loopUseCase.start(id, CLIENT_ACCOUNT_ID, List.of(
                new FloorInput(ConditionType.AMOUNT, "3000000"),
                new FloorInput(ConditionType.OTHER, "A 안")));
        agentUseCase.runAgent(id, NegotiationEventType.STARTED);   // 라운드 1
        driveRedirectsUntilFinalOfferOrFail(id, amountId);         // 라운드 15까지 소진

        Negotiation reloaded = negotiationRepository.findById(id).orElseThrow();
        // 라운드 상한에서 절충 불가 조건(OTHER)이 남아 최종 절충안을 만들지 못하고 결렬한다.
        assertThat(reloaded.getStatus()).isEqualTo(NegotiationStatus.FAILED);
        assertThat(reloaded.isFinalOffer()).isFalse();   // 최종 절충안 자체를 만들지 않았다
        assertThat(messageRepository.findByNegotiationId(id))
                .anyMatch(m -> m.getContent().contains("절충할 수 없는 조건"));
    }
}
