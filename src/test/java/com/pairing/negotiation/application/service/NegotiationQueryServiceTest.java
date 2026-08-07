package com.pairing.negotiation.application.service;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.repository.ClientProfileRepository;
import com.pairing.account.domain.repository.FreelancerProfileRepository;
import com.pairing.global.exception.BusinessException;
import com.pairing.negotiation.application.result.NegotiationView;
import com.pairing.negotiation.application.usecase.NegotiationQueryUseCase;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.model.PartyRole;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import com.pairing.negotiation.exception.NegotiationErrorCode;
import com.pairing.negotiation.presentation.api.response.NegotiationResponse;
import com.pairing.negotiation.presentation.api.support.NegotiationResponseFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
    private ClientProfileRepository clientProfileRepository;
    @Autowired
    private FreelancerProfileRepository freelancerProfileRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long CLIENT_ACCOUNT_ID = 1001L;
    private static final Long FREELANCER_ACCOUNT_ID = 1002L;
    private static final Long STRANGER_ACCOUNT_ID = 1003L;
    private static final Long PROJECT_ID = 7000L;

    private Long freelancerProfileId;
    private Long negotiationId;

    @BeforeEach
    void setUp() {
        Long clientProfileId = clientProfileRepository.save(ClientProfile.create(
                CLIENT_ACCOUNT_ID, "삼성전자", "1234567890",
                BusinessField.IT_CONTENTS_AI, EmployeeCount.SIZE_50_299)).getId();
        freelancerProfileId = freelancerProfileRepository.save(
                FreelancerProfile.create(FREELANCER_ACCOUNT_ID, LocalDate.of(1990, 1, 1))).getId();

        // project 는 협상 소유의 읽기 전용 엔티티(id/client_id/title)만 매핑되므로 직접 삽입한다.
        jdbcTemplate.update("INSERT INTO project (id, client_id, title) VALUES (?, ?, ?)",
                PROJECT_ID, clientProfileId, "페어링 웹 리뉴얼");

        Negotiation negotiation = Negotiation.create(100L, PROJECT_ID, 10L, freelancerProfileId,
                50_000_000L, List.of(NegotiationCondition.create(ConditionType.AMOUNT, "3200000", "4000000", 0)));
        NegotiationCondition amount = negotiation.getConditions().get(0);
        amount.submitFloor(PartyRole.CLIENT, "3500000");
        amount.submitFloor(PartyRole.FREELANCER, "3800000");
        negotiationId = negotiationRepository.save(negotiation).getId();
    }

    @Test
    @DisplayName("프리랜서가 상세를 열면 role=FREELANCER, 내 마지노선(3800000)만 노출된다")
    void detailAsFreelancer() {
        NegotiationView view = queryUseCase.getDetail(negotiationId, FREELANCER_ACCOUNT_ID);
        assertThat(view.viewerRole()).isEqualTo(PartyRole.FREELANCER);

        NegotiationResponse.Condition condition = NegotiationResponseFactory.detail(view).conditions().get(0);
        assertThat(condition.myFloor()).isEqualTo("3800000");
        // 희망값은 양측 공개
        assertThat(condition.clientValue()).isEqualTo("3200000");
        assertThat(condition.freelancerValue()).isEqualTo("4000000");
    }

    @Test
    @DisplayName("클라이언트가 상세를 열면 role=CLIENT, 내 마지노선(3500000)만 노출된다")
    void detailAsClient() {
        NegotiationView view = queryUseCase.getDetail(negotiationId, CLIENT_ACCOUNT_ID);
        assertThat(view.viewerRole()).isEqualTo(PartyRole.CLIENT);
        assertThat(view.projectTitle()).isEqualTo("페어링 웹 리뉴얼");

        NegotiationResponse.Condition condition = NegotiationResponseFactory.detail(view).conditions().get(0);
        assertThat(condition.myFloor()).isEqualTo("3500000");
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
        assertThatThrownBy(() -> queryUseCase.getDetail(999_999L, FREELANCER_ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(NegotiationErrorCode.NEGOTIATION_NOT_FOUND);
    }

    @Test
    @DisplayName("프리랜서 목록(projectId 없음)은 내가 프리인 협상을 CLIENT 아닌 FREELANCER 관점으로 반환")
    void findMineAsFreelancer() {
        List<NegotiationView> result = queryUseCase.findMine(FREELANCER_ACCOUNT_ID, null, null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).viewerRole()).isEqualTo(PartyRole.FREELANCER);
        assertThat(result.get(0).negotiation().getId()).isEqualTo(negotiationId);
    }

    @Test
    @DisplayName("클라 협상 탭(projectId 있음)은 프로젝트 소유자만 조회 가능")
    void findMineAsProjectOwner() {
        List<NegotiationView> result = queryUseCase.findMine(CLIENT_ACCOUNT_ID, PROJECT_ID, null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).viewerRole()).isEqualTo(PartyRole.CLIENT);
    }

    @Test
    @DisplayName("남의 프로젝트 협상 탭을 조회하면 NG_002 (권한 구멍 차단)")
    void findMineOfOthersProjectThrows() {
        assertThatThrownBy(() -> queryUseCase.findMine(STRANGER_ACCOUNT_ID, PROJECT_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(NegotiationErrorCode.NOT_PARTICIPANT);
    }
}
