package com.pairing.negotiation.application.service;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.repository.ClientProfileRepository;
import com.pairing.account.domain.repository.FreelancerProfileRepository;
import com.pairing.chat.domain.repository.ChatRoomRepository;
import com.pairing.global.exception.BusinessException;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.negotiation.application.command.CreateNegotiationCommand;
import com.pairing.negotiation.application.usecase.NegotiationCommandUseCase;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.FreelancerConditionSnapshot;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.model.NegotiationMessage;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.domain.repository.NegotiationMessageRepository;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import com.pairing.negotiation.domain.service.NegotiationLogVerifier;
import com.pairing.negotiation.exception.NegotiationErrorCode;
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
 * 매칭 수락 → 협상 생성(동기) 검증. 클라 희망값(project)과 프리 스냅샷을 비교해 불일치 조건만
 * 저장되는지, 애그리거트가 영속되는지 확인한다.
 */
@SpringBootTest
@Transactional
class NegotiationCommandServiceTest {

    @Autowired
    private NegotiationCommandUseCase commandUseCase;
    @Autowired
    private NegotiationRepository negotiationRepository;
    @Autowired
    private NegotiationMessageRepository messageRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ClientProfileRepository clientProfileRepository;
    @Autowired
    private FreelancerProfileRepository freelancerProfileRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long PROJECT_ID = 8000L;

    private Long clientProfileId;
    private Long freelancerProfileId;

    /**
     * 프로젝트와 그 소유 프로필을 함께 만든다.
     *
     * <p>프로필을 실제로 만드는 이유: 즉시 타결 경로가 계약서를 생성하고, 계약은 양측 <b>계정 ID</b>를
     * 프로필에서 역으로 찾는다. 존재하지 않는 ID 를 박아 두면 계약 생성이 실패하면서 협상 생성까지
     * 같은 트랜잭션으로 롤백된다.
     */
    private void insertProject(Long budgetAmount, WorkStyle workStyle, WorkForm workForm,
                               LocalDate startDesiredDate, boolean startNegotiable) {
        clientProfileId = clientProfileRepository.save(ClientProfile.create(
                910_201L, "삼성전자", "1234567890",
                BusinessField.IT_CONTENTS_AI, EmployeeCount.SIZE_50_299, "서울 강남구 테헤란로 1")).getId();
        freelancerProfileId = freelancerProfileRepository.save(
                FreelancerProfile.create(910_202L, LocalDate.of(1990, 1, 1))).getId();

        // project 는 project 도메인 소유다. 그쪽 엔티티의 NOT NULL 컬럼이 늘면 여기도 채워야 한다.
        jdbcTemplate.update("INSERT INTO project "
                        + "(id, client_id, title, budget_amount, work_style, work_form, "
                        + "start_desired_date, start_negotiable, "
                        + "period_value, period_unit, status, payment_status, "
                        + "total_headcount, confirmed_headcount, "
                        + "extension_count, free_rerecommend_used, paid_rerecommend_used) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                PROJECT_ID, clientProfileId, "페어링 웹 리뉴얼", budgetAmount,
                workStyle.name(), workForm.name(), startDesiredDate, startNegotiable,
                6, "MONTH", "RECRUITING", "DEPOSIT_PAID", 1, 0, 0, 0, 0);

        // 즉시 타결이면 매칭 요청도 CONTRACT_PENDING 으로 올리므로(markNegotiationAgreed) 대상 행이 있어야
        // 한다. id 는 command() 가 requestId 로 넘기는 값(100L)과 맞춘다. NEGOTIATING 인 이유는
        // agreeNegotiation() 이 그 상태만 허용해서다 — 실제 흐름에서도 매칭이 협상 생성 전에 저장해둔다.
        jdbcTemplate.update("INSERT INTO matching_request "
                        + "(id, project_id, position_id, candidate_id, freelancer_id, "
                        + "status, requested_at, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                100L, PROJECT_ID, 10L, 1L, freelancerProfileId, "NEGOTIATING");
    }

    private CreateNegotiationCommand command(Long budgetCap, FreelancerConditionSnapshot snapshot) {
        return new CreateNegotiationCommand(100L, PROJECT_ID, 10L, freelancerProfileId, budgetCap, snapshot);
    }

    @Test
    @DisplayName("불일치 조건만 담긴 협상이 생성·영속된다 (AMOUNT·근무방식·근무형태·착수일)")
    void createWithMismatches() {
        insertProject(5_000_000L, WorkStyle.ONSITE, WorkForm.FULL_TIME, LocalDate.of(2026, 1, 1), false);

        Long id = commandUseCase.create(command(4_800_000L,
                new FreelancerConditionSnapshot(PayUnit.MONTHLY, 6_000_000L,
                        WorkStyle.REMOTE, WorkForm.PART_TIME, LocalDate.of(2026, 3, 1), false,
                        5_500_000L, null, null)));

        Negotiation saved = negotiationRepository.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(NegotiationStatus.IN_PROGRESS);
        assertThat(saved.getBudgetCap()).isEqualTo(4_800_000L);
        assertThat(saved.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(saved.getFreelancerId()).isEqualTo(freelancerProfileId);
        assertThat(saved.getConditions())
                .extracting(NegotiationCondition::getConditionType)
                .containsExactlyInAnyOrder(ConditionType.AMOUNT, ConditionType.WORK_STYLE,
                        ConditionType.WORK_FORM, ConditionType.START_DATE);
    }

    @Test
    @DisplayName("모든 조건이 맞으면 협상 없이 즉시 타결된다(채팅방은 계약 체결 시 열린다)")
    void createWithNoMismatchSettlesImmediately() {
        Long clientProfileId = clientProfileRepository.save(ClientProfile.create(
                910_101L, "삼성전자", "1234567890",
                BusinessField.IT_CONTENTS_AI, EmployeeCount.SIZE_50_299, "서울 강남구 테헤란로 1")).getId();
        Long freelancerProfileId = freelancerProfileRepository.save(
                FreelancerProfile.create(910_102L, LocalDate.of(1990, 1, 1))).getId();
        jdbcTemplate.update("INSERT INTO project "
                        + "(id, client_id, title, budget_amount, work_style, work_form, "
                        + "start_desired_date, start_negotiable, "
                        + "period_value, period_unit, status, payment_status, "
                        + "total_headcount, confirmed_headcount, "
                        + "extension_count, free_rerecommend_used, paid_rerecommend_used) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                PROJECT_ID, clientProfileId, "페어링 웹 리뉴얼", 5_000_000L,
                WorkStyle.REMOTE.name(), WorkForm.FULL_TIME.name(), LocalDate.of(2026, 1, 1), true,
                6, "MONTH", "RECRUITING", "DEPOSIT_PAID", 1, 0, 0, 0, 0);

        // 이 테스트가 즉시 타결 경로다 — markNegotiationAgreed 가 올릴 매칭 요청이 있어야 한다.
        // 이 테스트는 setUp 픽스처를 안 쓰고 자기 데이터를 따로 심어서 여기도 넣는다.
        jdbcTemplate.update("INSERT INTO matching_request "
                        + "(id, project_id, position_id, candidate_id, freelancer_id, "
                        + "status, requested_at, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                100L, PROJECT_ID, 10L, 1L, freelancerProfileId, "NEGOTIATING");

        Long id = commandUseCase.create(new CreateNegotiationCommand(100L, PROJECT_ID, 10L, freelancerProfileId,
                5_000_000L,
                new FreelancerConditionSnapshot(PayUnit.MONTHLY, 5_000_000L,
                        WorkStyle.REMOTE, WorkForm.FULL_TIME, LocalDate.of(2026, 1, 1), false,
                        null, null, null)));

        Negotiation saved = negotiationRepository.findById(id).orElseThrow();
        assertThat(saved.getConditions()).isEmpty();
        assertThat(saved.getStatus()).isEqualTo(NegotiationStatus.AGREED);
        assertThat(saved.getAgreedAmount()).isEqualTo(5_000_000L);
        // 타결만으로는 방이 생기지 않는다. 계약이 체결돼야 열린다.
        assertThat(chatRoomRepository.findByNegotiationId(id)).isEmpty();

        // 최종 조건이 해시체인 로그에 봉인되고, 그 체인이 유효하다(증거).
        List<NegotiationMessage> logs = messageRepository.findByNegotiationId(id);
        assertThat(logs).anyMatch(m -> m.getContent().contains("봉인")
                && m.getContent().contains("agreedAmount=5000000"));
        assertThat(NegotiationLogVerifier.verify(logs).valid()).isTrue();

        // 매칭 요청도 계약 대기로 넘어가야 한다. 이게 빠져 있어서 즉시 타결 건만 NEGOTIATING 에
        // 갇혀 있었다(라운드를 도는 경로는 NegotiationLoopService 가 이미 처리하고 있었음).
        String requestStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM matching_request WHERE id = ?", String.class, 100L);
        assertThat(requestStatus).isEqualTo("CONTRACT_PENDING");
    }

    @Test
    @DisplayName("즉시 타결 금액은 예산 상한이 아니라 프리랜서가 제시한 월 단가다")
    void immediateSettlementUsesFreelancerMonthlyPay() {
        insertProject(5_000_000L, WorkStyle.REMOTE, WorkForm.FULL_TIME, LocalDate.of(2026, 1, 1), true);

        // 프리 월 300만 <= 상한 500만 → 다툴 게 없어 조건 0개로 즉시 타결.
        Long id = commandUseCase.create(command(5_000_000L,
                new FreelancerConditionSnapshot(PayUnit.MONTHLY, 3_000_000L,
                        WorkStyle.REMOTE, WorkForm.FULL_TIME, LocalDate.of(2026, 1, 1), false,
                        null, null, null)));

        Negotiation saved = negotiationRepository.findById(id).orElseThrow();
        assertThat(saved.getConditions()).isEmpty();
        assertThat(saved.getStatus()).isEqualTo(NegotiationStatus.AGREED);
        // 상한(500만)이 아니라 프리 제시액(300만). 상한을 쓰면 아무도 제시한 적 없는 금액이 계약서에 찍힌다.
        assertThat(saved.getAgreedAmount()).isEqualTo(3_000_000L);
        assertThat(saved.getFreelancerMonthlyPay()).isEqualTo(3_000_000L);
    }

    @Test
    @DisplayName("AMOUNT 조건은 양측 값을 모두 월 단가로 담는다(클라 쪽에 총예산이 섞이지 않는다)")
    void amountConditionUsesMonthlyUnitOnBothSides() {
        // 프로젝트 총예산 5천만, 협상 상한(월 단가)은 480만.
        insertProject(50_000_000L, WorkStyle.REMOTE, WorkForm.FULL_TIME, LocalDate.of(2026, 1, 1), true);

        Long id = commandUseCase.create(command(4_800_000L,
                new FreelancerConditionSnapshot(PayUnit.MONTHLY, 6_000_000L,
                        WorkStyle.REMOTE, WorkForm.FULL_TIME, LocalDate.of(2026, 1, 1), false,
                        null, null, null)));

        NegotiationCondition amount = negotiationRepository.findById(id).orElseThrow()
                .getConditions().stream()
                .filter(c -> c.getConditionType() == ConditionType.AMOUNT)
                .findFirst().orElseThrow();
        assertThat(amount.getClientValue()).isEqualTo("4800000");        // 월 단가 상한(총예산 5천만 아님)
        assertThat(amount.getFreelancerValue()).isEqualTo("6000000");    // 프리 월 단가
    }

    @Test
    @DisplayName("프로젝트가 없으면 생성 실패(NG_004) → 매칭 수락까지 롤백되게 예외를 던진다")
    void createFailsWhenProjectMissing() {
        assertThatThrownBy(() -> commandUseCase.create(command(5_000_000L,
                new FreelancerConditionSnapshot(PayUnit.MONTHLY, 5_000_000L,
                        WorkStyle.ANY, WorkForm.ANY, null, true, null, null, null))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(NegotiationErrorCode.INVALID_CONDITION);
    }
}
