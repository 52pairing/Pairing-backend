package com.pairing.negotiation.application.service;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.repository.ClientProfileRepository;
import com.pairing.account.domain.repository.FreelancerProfileRepository;
import com.pairing.negotiation.application.port.out.NegotiationProposalPort;
import com.pairing.negotiation.application.usecase.NegotiationLoopUseCase;
import com.pairing.negotiation.application.usecase.NegotiationLoopUseCase.FloorInput;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 협상 알림 <b>배선</b> 검증 — 발행(publishEvent)부터 알림 행 저장까지 실제로 이어지는지.
 *
 * <p><b>{@code @Transactional} 을 일부러 붙이지 않았다.</b> 알림은
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 로 나가므로 <b>커밋이 실제로 일어나야</b> 뜬다.
 * 테스트가 트랜잭션을 잡고 롤백하면 리스너가 아예 호출되지 않아, 배선이 끊겨 있어도 초록불이 된다 —
 * 2026-08-13 운영에서 알림이 0건이던 문제를 기존 테스트가 못 잡은 이유가 이것이다.
 */
@SpringBootTest
class NegotiationNotificationWiringTest {

    /** 대리인 호출을 결정적으로 만든다(파이썬 HTTP 없이). 라운드 1 제안 1건만 돌려준다. */
    @TestConfiguration
    static class StubProposalConfig {
        @Bean
        @Primary
        NegotiationProposalPort stubProposalPort() {
            return context -> {
                var messages = context.conditions().stream()
                        .map(c -> new NegotiationProposalPort.AgentMessage(
                                "CLIENT_AGENT", c.conditionId(), "PROPOSAL",
                                "2000000", "중간값을 제안합니다.", "테스트 제안"))
                        .toList();
                var outcomes = context.conditions().stream()
                        .map(c -> new NegotiationProposalPort.ConditionOutcome(c.conditionId(), "2000000", false))
                        .toList();
                return new NegotiationProposalPort.A2AResult(messages, outcomes);
            };
        }
    }

    private static final Long CLIENT_ACCOUNT_ID = 930_001L;
    private static final Long FREELANCER_ACCOUNT_ID = 930_002L;
    private static final Long PROJECT_ID = 9300L;

    @Autowired
    private NegotiationLoopUseCase loopUseCase;
    @Autowired
    private NegotiationRepository negotiationRepository;
    @Autowired
    private ClientProfileRepository clientProfileRepository;
    @Autowired
    private FreelancerProfileRepository freelancerProfileRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long negotiationId;

    @BeforeEach
    void setUp() {
        // 커밋해서 준비한다. AFTER_COMMIT 리스너가 보려면 데이터가 실제로 커밋돼 있어야 한다.
        negotiationId = transactionTemplate.execute(status -> {
            jdbcTemplate.update("DELETE FROM notification WHERE owner_account_id IN (?, ?)",
                    CLIENT_ACCOUNT_ID, FREELANCER_ACCOUNT_ID);

            Long clientProfileId = clientProfileRepository.save(ClientProfile.create(
                    CLIENT_ACCOUNT_ID, "유어커피", "1234500000",
                    BusinessField.IT_CONTENTS_AI, EmployeeCount.SIZE_50_299, "서울")).getId();
            Long freelancerProfileId = freelancerProfileRepository.save(
                    FreelancerProfile.create(FREELANCER_ACCOUNT_ID, LocalDate.of(1990, 1, 1))).getId();

            jdbcTemplate.update("INSERT INTO project "
                            + "(id, client_id, title, start_negotiable, period_value, period_unit, "
                            + "budget_amount, work_style, work_form, status, payment_status, "
                            + "total_headcount, confirmed_headcount, extension_count, "
                            + "free_rerecommend_used, paid_rerecommend_used) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    PROJECT_ID, clientProfileId, "로봇개 프로그램 개발", true,
                    3, "MONTH", 50_000_000L, "ANY", "ANY",
                    "RECRUITING", "DEPOSIT_PAID", 1, 0, 0, 0, 0);

            Negotiation negotiation = Negotiation.create(9301L, PROJECT_ID, 9302L, freelancerProfileId,
                    1_500_000L, 1_500_000L,
                    List.of(NegotiationCondition.create(ConditionType.AMOUNT, "1500000", "5500000", 0)));
            return negotiationRepository.save(negotiation).getId();
        });
    }

    @Test
    @DisplayName("양측 마지노선이 모두 모이면 협상 시작 알림이 양쪽에 저장된다")
    void savesStartedNotificationToBothParties() {
        // 한쪽만 제출 — 아직 대기 상태라 알림이 없어야 한다.
        loopUseCase.start(negotiationId, FREELANCER_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "4400000")));
        assertThat(countNotifications()).isZero();

        // 나머지 한쪽 제출 → 여기서 협상이 시작되고 알림이 나가야 한다.
        loopUseCase.start(negotiationId, CLIENT_ACCOUNT_ID,
                List.of(new FloorInput(ConditionType.AMOUNT, "1500000")));

        // STARTED 만 세는 이유: 대리인(@Async)이 라운드 1 을 끝내면 PROPOSED 2건이 뒤따라 들어온다.
        // 전체 건수로 단정하면 그 타이밍에 따라 초록불/빨간불이 갈리는 깨지는 테스트가 된다.
        List<Long> owners = jdbcTemplate.queryForList(
                "SELECT owner_account_id FROM notification "
                        + "WHERE type = 'NEGOTIATION_STARTED' AND owner_account_id IN (?, ?)",
                Long.class, CLIENT_ACCOUNT_ID, FREELANCER_ACCOUNT_ID);

        // 양측에 각각 한 건. 한쪽에 두 건이 가거나 한쪽이 빠지면 실패한다.
        assertThat(owners).containsExactlyInAnyOrder(CLIENT_ACCOUNT_ID, FREELANCER_ACCOUNT_ID);
    }

    private int countNotifications() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE owner_account_id IN (?, ?)",
                Integer.class, CLIENT_ACCOUNT_ID, FREELANCER_ACCOUNT_ID);
        return count == null ? 0 : count;
    }
}
