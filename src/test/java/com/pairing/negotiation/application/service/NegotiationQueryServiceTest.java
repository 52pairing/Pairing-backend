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
import com.pairing.negotiation.application.result.NegotiationView;
import com.pairing.negotiation.application.usecase.NegotiationQueryUseCase;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.model.NegotiationMessage;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                BusinessField.IT_CONTENTS_AI, EmployeeCount.SIZE_50_299)).getId();
        freelancerProfileId = freelancerProfileRepository.save(
                FreelancerProfile.create(freelancerAccountId, LocalDate.of(1990, 1, 1))).getId();

        // project 는 협상 소유의 읽기 전용 엔티티만 매핑되므로 직접 삽입한다.
        // start_negotiable 은 NOT NULL(primitive)이라 반드시 채운다.
        jdbcTemplate.update("INSERT INTO project (id, client_id, title, start_negotiable) VALUES (?, ?, ?, ?)",
                PROJECT_ID, clientProfileId, "페어링 웹 리뉴얼", true);

        Negotiation negotiation = Negotiation.create(100L, PROJECT_ID, 10L, freelancerProfileId,
                50_000_000L, List.of(NegotiationCondition.create(ConditionType.AMOUNT, "3200000", "4000000", 0)));
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
