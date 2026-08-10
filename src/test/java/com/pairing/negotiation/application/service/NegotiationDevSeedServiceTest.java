package com.pairing.negotiation.application.service;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.account.domain.repository.ClientProfileRepository;
import com.pairing.global.exception.BusinessException;
import com.pairing.negotiation.application.usecase.NegotiationQueryUseCase;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.domain.repository.NegotiationMessageRepository;
import com.pairing.negotiation.domain.service.NegotiationLogVerifier;
import com.pairing.negotiation.presentation.api.response.NegotiationResponse;
import com.pairing.negotiation.presentation.api.support.NegotiationResponseFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 프론트 연동용 더미 생성 검증. 시드가 만든 협상이 <b>실제 조회 API 로 정상적으로 읽히는지</b>까지 본다.
 * (더미가 화면에서 안 뜨면 프론트가 다시 막히므로, 생성만이 아니라 조회 결과를 확인한다)
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "app.dev.seed-enabled=true")
class NegotiationDevSeedServiceTest {

    @Autowired
    private NegotiationDevSeedService devSeedService;
    @Autowired
    private NegotiationQueryUseCase queryUseCase;
    @Autowired
    private NegotiationMessageRepository messageRepository;
    @Autowired
    private ClientProfileRepository clientProfileRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long CLIENT_ACCOUNT_ID = 930_001L;
    private static final Long STRANGER_ACCOUNT_ID = 930_002L;
    private static final Long PROJECT_ID = 8300L;

    @BeforeEach
    void setUp() {
        Long clientProfileId = clientProfileRepository.save(ClientProfile.create(
                CLIENT_ACCOUNT_ID, "삼성전자", "1234567890",
                BusinessField.IT_CONTENTS_AI, EmployeeCount.SIZE_50_299, "서울 강남구 테헤란로 1")).getId();

        jdbcTemplate.update("INSERT INTO project "
                        + "(id, client_id, title, start_negotiable, "
                        + "period_value, period_unit, budget_amount, work_style, work_form, "
                        + "status, payment_status, total_headcount, confirmed_headcount, "
                        + "extension_count, free_rerecommend_used, paid_rerecommend_used) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                PROJECT_ID, clientProfileId, "쇼핑몰", true,
                6, "MONTH", 50_000_000L, "REMOTE", "FULL_TIME",
                "RECRUITING", "DEPOSIT_PAID", 1, 0, 0, 0, 0);
    }

    private Map<NegotiationDevSeedService.Scenario, Long> seed() {
        return devSeedService.seed(PROJECT_ID, CLIENT_ACCOUNT_ID).stream()
                .collect(Collectors.toMap(
                        NegotiationDevSeedService.SeededNegotiation::scenario,
                        NegotiationDevSeedService.SeededNegotiation::negotiationId));
    }

    private NegotiationResponse detail(Long negotiationId) {
        return NegotiationResponseFactory.detail(queryUseCase.getDetail(negotiationId, CLIENT_ACCOUNT_ID));
    }

    @Test
    @DisplayName("시나리오별 협상이 만들어지고 상세 조회로 읽힌다")
    void seedsAllScenarios() {
        Map<NegotiationDevSeedService.Scenario, Long> ids = seed();

        assertThat(ids).hasSize(NegotiationDevSeedService.Scenario.values().length);

        // 마지노선 입력 전: 라운드 0 → 프론트가 "협상 시작" 화면으로 분기
        NegotiationResponse before = detail(ids.get(NegotiationDevSeedService.Scenario.BEFORE_START));
        assertThat(before.totalRound()).isZero();
        assertThat(before.status()).isEqualTo(NegotiationStatus.IN_PROGRESS);
        assertThat(before.conditions()).hasSize(3);

        // 내 응답 차례: 승인 패널 노출 조건
        NegotiationResponse waiting = detail(ids.get(NegotiationDevSeedService.Scenario.WAITING_FOR_ME));
        assertThat(waiting.waitingForMe()).isTrue();
        assertThat(waiting.totalRound()).isEqualTo(1);

        // 타결/결렬 상태
        assertThat(detail(ids.get(NegotiationDevSeedService.Scenario.AGREED)).status())
                .isEqualTo(NegotiationStatus.AGREED);
        assertThat(detail(ids.get(NegotiationDevSeedService.Scenario.FAILED)).status())
                .isEqualTo(NegotiationStatus.FAILED);
    }

    @Test
    @DisplayName("A2A 대화 로그가 함께 생기고 해시 체인이 유효하다")
    void seedsValidMessageChain() {
        Long id = seed().get(NegotiationDevSeedService.Scenario.WAITING_FOR_ME);

        var logs = messageRepository.findByNegotiationId(id);
        assertThat(logs).isNotEmpty();
        assertThat(logs).anyMatch(m -> m.getReason() != null);       // 근거 필수(요구사항 P13)
        // 시드도 실제 저장 규칙(해시 체인)을 지켜야 log-integrity 검증이 통과한다.
        assertThat(NegotiationLogVerifier.verify(logs).valid()).isTrue();
    }

    @Test
    @DisplayName("남의 프로젝트에는 만들 수 없다(NG_002)")
    void rejectsNonOwner() {
        assertThatThrownBy(() -> devSeedService.seed(PROJECT_ID, STRANGER_ACCOUNT_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("목록 조회에도 잡힌다(협상 탭이 비어 보이지 않는다)")
    void seededNegotiationsAppearInList() {
        seed();

        var page = queryUseCase.findMine(CLIENT_ACCOUNT_ID, PROJECT_ID, null,
                org.springframework.data.domain.PageRequest.of(0, 20));
        assertThat(page.getTotalElements())
                .isEqualTo(NegotiationDevSeedService.Scenario.values().length);
        assertThat(page.getContent()).extracting(v -> v.negotiation().getStatus())
                .contains(NegotiationStatus.IN_PROGRESS, NegotiationStatus.AGREED, NegotiationStatus.FAILED);
    }
}
