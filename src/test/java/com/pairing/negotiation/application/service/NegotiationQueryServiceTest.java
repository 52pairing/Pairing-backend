package com.pairing.negotiation.application.service;

import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.model.Role;
import com.pairing.account.domain.repository.AccountRepository;
import com.pairing.account.domain.repository.ClientProfileRepository;
import com.pairing.account.domain.repository.FreelancerProfileRepository;
import com.pairing.global.exception.BusinessException;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.negotiation.application.port.out.ProjectReaderPort;
import com.pairing.negotiation.application.result.AgreedNegotiationView;
import com.pairing.negotiation.application.result.NegotiationView;
import com.pairing.negotiation.application.usecase.NegotiationQueryUseCase;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.model.NegotiationMessage;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.domain.model.PartyRole;
import com.pairing.negotiation.domain.model.SenderType;
import com.pairing.negotiation.domain.repository.NegotiationMessageRepository;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import com.pairing.negotiation.exception.NegotiationErrorCode;
import com.pairing.negotiation.presentation.api.response.NegotiationResponse;
import com.pairing.negotiation.presentation.api.support.NegotiationResponseFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 협상 조회 전환 검증. 핵심은 "로그인 계정(account.id) → 명함 ID 번역 → role 판정"이 맞는지,
 * 그리고 role 에 따라 내 마지노선(myFloor)만 노출되고 당사자 아닌 사람은 NG_002 인지다.
 */
@SpringBootTest
@Transactional
class NegotiationQueryServiceTest {

    @Autowired
    private NegotiationQueryUseCase queryUseCase;
    @Autowired
    private NegotiationRepository negotiationRepository;
    @Autowired
    private NegotiationMessageRepository messageRepository;
    @Autowired
    private ClientProfileRepository clientProfileRepository;
    @Autowired
    private FreelancerProfileRepository freelancerProfileRepository;
    @Autowired
    private AccountRepository accountRepository;
    @MockBean
    private ProjectReaderPort projectReaderPort;

    private static final Long CLIENT_ACCOUNT_ID = 900_001L;   // client 판정은 client_profile.account_id 로만 하므로 account 행 불필요
    private static final Long STRANGER_ACCOUNT_ID = 900_003L;
    private static final Long PROJECT_ID = 7000L;

    private Long freelancerAccountId;   // account 행이 있어야 프리 이름(account.name)이 해석됨
    private Long freelancerProfileId;
    private Long negotiationId;
    private Long amountConditionId;

    @BeforeEach
    void setUp() {
        freelancerAccountId = accountRepository.save(Account.createByEmail(
                "freelancer@pairing.test", "hash", Role.FREELANCER, "김프리", "01011112222")).getId();

        Long clientProfileId = clientProfileRepository.save(ClientProfile.create(
                CLIENT_ACCOUNT_ID, "삼성전자", "1234567890",
                BusinessField.IT_CONTENTS_AI, EmployeeCount.SIZE_50_299, "서울 강남구 테헤란로 1")).getId();
        freelancerProfileId = freelancerProfileRepository.save(
                FreelancerProfile.create(freelancerAccountId, LocalDate.of(1990, 1, 1))).getId();

        // project 는 project 도메인 소유다. 여기선 그 조회 포트를 목킹해 협상 조회 로직만 검증한다
        // (project 테이블 스키마 변화에 협상 테스트가 흔들리지 않게).
        when(projectReaderPort.findById(PROJECT_ID)).thenReturn(Optional.of(
                new ProjectReaderPort.ProjectView(PROJECT_ID, clientProfileId, "페어링 웹 리뉴얼",
                        50_000_000L, WorkStyle.REMOTE, WorkForm.FULL_TIME,
                        LocalDate.of(2026, 1, 1), true, 6, PeriodUnit.MONTH)));

        Negotiation negotiation = Negotiation.create(100L, PROJECT_ID, 10L, freelancerProfileId,
                50_000_000L, 50_000_000L, List.of(NegotiationCondition.create(ConditionType.AMOUNT, "3200000", "4000000", 0)));
        NegotiationCondition amount = negotiation.getConditions().get(0);
        amount.submitFloor(PartyRole.CLIENT, "3500000");
        amount.submitFloor(PartyRole.FREELANCER, "3800000");
        Negotiation saved = negotiationRepository.save(negotiation);
        negotiationId = saved.getId();
        amountConditionId = saved.getConditions().get(0).getId();
    }

    /** 라운드 1로 진행시키고 AMOUNT 조건에 AI 제안 1건을 남긴다(SYSTEM 중재자 제안). */
    private void proposeRound1() {
        Negotiation n = negotiationRepository.findById(negotiationId).orElseThrow();
        n.incrementRound();
        negotiationRepository.save(n);
        messageRepository.saveAll(List.of(NegotiationMessage.proposal(negotiationId, amountConditionId, 1,
                SenderType.SYSTEM, "월 360만원을 제안합니다.", "양측 마지노선의 중간값", "3600000")));
    }

    @Test
    @DisplayName("프리랜서가 상세를 열면 role=FREELANCER, 내 마지노선(3800000)만·상대 이름=회사명")
    void detailAsFreelancer() {
        NegotiationView view = queryUseCase.getDetail(negotiationId, freelancerAccountId);
        assertThat(view.viewerRole()).isEqualTo(PartyRole.FREELANCER);

        NegotiationResponse response = NegotiationResponseFactory.detail(view);
        assertThat(response.counterpartName()).isEqualTo("삼성전자");   // 프리가 보면 상대=클라 회사명
        NegotiationResponse.Condition condition = response.conditions().get(0);
        assertThat(condition.myFloor()).isEqualTo("3800000");
        // 희망값은 양측 공개
        assertThat(condition.clientValue()).isEqualTo("3200000");
        assertThat(condition.freelancerValue()).isEqualTo("4000000");
    }

    @Test
    @DisplayName("클라이언트가 상세를 열면 role=CLIENT, 내 마지노선(3500000)만·상대 이름=프리 이름")
    void detailAsClient() {
        NegotiationView view = queryUseCase.getDetail(negotiationId, CLIENT_ACCOUNT_ID);
        assertThat(view.viewerRole()).isEqualTo(PartyRole.CLIENT);
        assertThat(view.projectTitle()).isEqualTo("페어링 웹 리뉴얼");

        NegotiationResponse response = NegotiationResponseFactory.detail(view);
        assertThat(response.counterpartName()).isEqualTo("김프리");   // 클라가 보면 상대=프리 이름
        assertThat(response.conditions().get(0).myFloor()).isEqualTo("3500000");
    }

    @Test
    @DisplayName("결렬이면 종료 사유·시각이 내려간다 — 화면이 '왜 끝났는지'를 그대로 보여줄 수 있어야 한다")
    void detailExposesEndReasonWhenFailed() {
        Negotiation negotiation = negotiationRepository.findById(negotiationId).orElseThrow();
        negotiation.fail("근무 형태 조건 차이가 좁혀지지 않아 협상을 종료합니다.");
        negotiationRepository.save(negotiation);

        NegotiationResponse response = NegotiationResponseFactory.detail(
                queryUseCase.getDetail(negotiationId, CLIENT_ACCOUNT_ID));

        assertThat(response.status()).isEqualTo(NegotiationStatus.FAILED);
        assertThat(response.endReason()).isEqualTo("근무 형태 조건 차이가 좁혀지지 않아 협상을 종료합니다.");
        assertThat(response.endedAt()).isNotNull();
    }

    @Test
    @DisplayName("타결에는 종료 사유가 없다 — 성공 카드에 사유가 붙으면 안 된다")
    void detailHasNoEndReasonWhenAgreed() {
        Negotiation negotiation = negotiationRepository.findById(negotiationId).orElseThrow();
        negotiation.getConditions().forEach(condition -> condition.lock("3500000"));
        negotiation.agree(3_500_000L);
        negotiationRepository.save(negotiation);

        NegotiationResponse response = NegotiationResponseFactory.detail(
                queryUseCase.getDetail(negotiationId, CLIENT_ACCOUNT_ID));

        assertThat(response.status()).isEqualTo(NegotiationStatus.AGREED);
        assertThat(response.endReason()).isNull();
        // 끝난 시각은 타결에도 있다. "언제 끝났나"와 "왜 끝났나"는 다른 질문이다.
        assertThat(response.endedAt()).isNotNull();
    }

    @Test
    @DisplayName("상세: 조건 카드에 현재 AI 제안값·근거가 인라인으로 채워진다")
    void detailShowsCurrentProposal() {
        proposeRound1();

        NegotiationResponse response = NegotiationResponseFactory.detail(
                queryUseCase.getDetail(negotiationId, freelancerAccountId));
        NegotiationResponse.Condition condition = response.conditions().get(0);
        assertThat(condition.proposedValue()).isEqualTo("3600000");
        assertThat(condition.reason()).isEqualTo("양측 마지노선의 중간값");
    }

    @Test
    @DisplayName("목록: 제안 후 미응답이면 waitingForMe=true, 마지막 제안 주체·시각이 채워진다")
    void summaryWaitingForMeAndLastProposal() {
        proposeRound1();

        NegotiationView view = queryUseCase.findMine(
                freelancerAccountId, null, null, PageRequest.of(0, 10)).getContent().get(0);
        assertThat(view.waitingForMe()).isTrue();
        assertThat(view.lastProposalBy()).isEqualTo(SenderType.SYSTEM);
        assertThat(view.lastProposalAt()).isNotNull();
    }

    /** 계약용 조회 대상: AMOUNT 를 락하고 타결시킨다. */
    private void settleNegotiation() {
        Negotiation n = negotiationRepository.findById(negotiationId).orElseThrow();
        n.getConditions().get(0).lock("3600000");
        n.agree(3_600_000L);
        negotiationRepository.save(n);
    }

    @Test
    @DisplayName("상세: viewerRole 과 waitingForMe 가 내려간다(승인 패널 노출 판정용)")
    void detailExposesViewerRoleAndWaitingForMe() {
        NegotiationResponse before = NegotiationResponseFactory.detail(
                queryUseCase.getDetail(negotiationId, freelancerAccountId));
        assertThat(before.viewerRole()).isEqualTo(PartyRole.FREELANCER);
        assertThat(before.waitingForMe()).isFalse();   // 아직 제안 없음

        proposeRound1();

        NegotiationResponse after = NegotiationResponseFactory.detail(
                queryUseCase.getDetail(negotiationId, freelancerAccountId));
        assertThat(after.waitingForMe()).isTrue();     // 내 응답 차례 → 승인 패널
        assertThat(NegotiationResponseFactory.detail(
                queryUseCase.getDetail(negotiationId, CLIENT_ACCOUNT_ID)).viewerRole())
                .isEqualTo(PartyRole.CLIENT);
    }

    @Test
    @DisplayName("응답대기 건수: 제안이 오고 내 응답이 없으면 1건으로 센다")
    void waitingCountAfterProposal() {
        assertThat(queryUseCase.countWaitingForMe(freelancerAccountId)).isZero();   // 제안 전

        proposeRound1();

        assertThat(queryUseCase.countWaitingForMe(freelancerAccountId)).isEqualTo(1);
    }

    @Test
    @DisplayName("응답대기 건수: 내가 응답하면 0으로 줄고, 다음 라운드 제안이 오면 다시 1")
    void waitingCountAfterResponse() {
        proposeRound1();
        messageRepository.saveAll(List.of(NegotiationMessage.response(negotiationId, amountConditionId, 1,
                SenderType.FREELANCER, "제안을 수락했습니다.", "YES", freelancerAccountId)));

        assertThat(queryUseCase.countWaitingForMe(freelancerAccountId)).isZero();

        // 라운드 2 제안이 오면 다시 내 차례다.
        Negotiation n = negotiationRepository.findById(negotiationId).orElseThrow();
        n.incrementRound();
        negotiationRepository.save(n);
        messageRepository.saveAll(List.of(NegotiationMessage.proposal(negotiationId, amountConditionId, 2,
                SenderType.SYSTEM, "월 370만원을 제안합니다.", "재협상", "3700000")));

        assertThat(queryUseCase.countWaitingForMe(freelancerAccountId)).isEqualTo(1);
    }

    @Test
    @DisplayName("응답대기 건수: 종료된 협상은 세지 않는다")
    void waitingCountExcludesEnded() {
        proposeRound1();
        Negotiation n = negotiationRepository.findById(negotiationId).orElseThrow();
        n.fail("테스트 종료");
        negotiationRepository.save(n);

        assertThat(queryUseCase.countWaitingForMe(freelancerAccountId)).isZero();
    }

    @Test
    @DisplayName("응답대기 건수: 당사자가 아니면 0")
    void waitingCountForStranger() {
        proposeRound1();

        assertThat(queryUseCase.countWaitingForMe(STRANGER_ACCOUNT_ID)).isZero();
    }

    @Test
    @DisplayName("응답대기 건수: 클라는 내가 소유한 프로젝트의 협상으로 센다")
    void waitingCountAsClient() {
        // 협상은 clientProfileId 를 갖지 않아 소유 프로젝트 목록으로 좁힌다.
        when(projectReaderPort.findMyProjectIds(CLIENT_ACCOUNT_ID)).thenReturn(List.of(PROJECT_ID));
        proposeRound1();

        assertThat(queryUseCase.countWaitingForMe(CLIENT_ACCOUNT_ID)).isEqualTo(1);

        // 클라가 응답하면 빠진다.
        messageRepository.saveAll(List.of(NegotiationMessage.response(negotiationId, amountConditionId, 1,
                SenderType.CLIENT, "제안을 수락했습니다.", "YES", CLIENT_ACCOUNT_ID)));
        assertThat(queryUseCase.countWaitingForMe(CLIENT_ACCOUNT_ID)).isZero();
    }

    @Test
    @DisplayName("계약용 조회: 뷰어 계정 없이 타결 스냅샷을 돌려주고, 합의값은 계약 표기 그대로다")
    void agreedForContract() {
        settleNegotiation();

        AgreedNegotiationView view = queryUseCase.getAgreedForContract(negotiationId);

        assertThat(view.negotiationId()).isEqualTo(negotiationId);
        assertThat(view.requestId()).isEqualTo(100L);
        assertThat(view.projectId()).isEqualTo(PROJECT_ID);
        assertThat(view.positionId()).isEqualTo(10L);
        assertThat(view.freelancerId()).isEqualTo(freelancerProfileId);
        assertThat(view.agreedAmount()).isEqualTo(3_600_000L);
        assertThat(view.conditions()).singleElement()
                .satisfies(c -> {
                    assertThat(c.conditionType()).isEqualTo(ConditionType.AMOUNT);
                    assertThat(c.agreedValue()).isEqualTo("3600000");
                });
    }

    @Test
    @DisplayName("계약용 조회: 타결 전 협상이면 NG_009")
    void agreedForContractBeforeSettlementThrows() {
        assertThatThrownBy(() -> queryUseCase.getAgreedForContract(negotiationId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(NegotiationErrorCode.NOT_AGREED);
    }

    @Test
    @DisplayName("계약용 조회: 없는 협상이면 NG_001")
    void agreedForContractNotFoundThrows() {
        assertThatThrownBy(() -> queryUseCase.getAgreedForContract(999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(NegotiationErrorCode.NEGOTIATION_NOT_FOUND);
    }

    @Test
    @DisplayName("당사자가 아니면 상세 조회는 NG_002")
    void detailAsStrangerThrows() {
        assertThatThrownBy(() -> queryUseCase.getDetail(negotiationId, STRANGER_ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(NegotiationErrorCode.NOT_PARTICIPANT);
    }

    @Test
    @DisplayName("없는 협상 상세는 NG_001")
    void detailNotFoundThrows() {
        assertThatThrownBy(() -> queryUseCase.getDetail(999_999L, freelancerAccountId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(NegotiationErrorCode.NEGOTIATION_NOT_FOUND);
    }

    @Test
    @DisplayName("프리랜서 목록(projectId 없음)은 FREELANCER 관점 + 회사명·프리 이름이 채워진다")
    void findMineAsFreelancer() {
        List<NegotiationView> result = queryUseCase.findMine(
                freelancerAccountId, null, null, PageRequest.of(0, 10)).getContent();
        assertThat(result).hasSize(1);
        NegotiationView view = result.get(0);
        assertThat(view.viewerRole()).isEqualTo(PartyRole.FREELANCER);
        assertThat(view.negotiation().getId()).isEqualTo(negotiationId);
        assertThat(view.clientName()).isEqualTo("삼성전자");
        assertThat(view.freelancerName()).isEqualTo("김프리");
    }

    @Test
    @DisplayName("클라 협상 탭(projectId 있음)은 프로젝트 소유자만 조회 가능")
    void findMineAsProjectOwner() {
        List<NegotiationView> result = queryUseCase.findMine(
                CLIENT_ACCOUNT_ID, PROJECT_ID, null, PageRequest.of(0, 10)).getContent();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).viewerRole()).isEqualTo(PartyRole.CLIENT);
    }

    @Test
    @DisplayName("남의 프로젝트 협상 탭을 조회하면 NG_002 (권한 구멍 차단)")
    void findMineOfOthersProjectThrows() {
        assertThatThrownBy(() -> queryUseCase.findMine(
                STRANGER_ACCOUNT_ID, PROJECT_ID, null, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(NegotiationErrorCode.NOT_PARTICIPANT);
    }
}
