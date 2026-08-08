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
import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import com.pairing.negotiation.exception.NegotiationErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ClientProfileRepository clientProfileRepository;
    @Autowired
    private FreelancerProfileRepository freelancerProfileRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long PROJECT_ID = 8000L;

    private void insertProject(Long budgetAmount, WorkStyle workStyle, WorkForm workForm,
                               LocalDate startDesiredDate, boolean startNegotiable) {
        jdbcTemplate.update("INSERT INTO project "
                        + "(id, client_id, title, budget_amount, work_style, work_form, start_desired_date, start_negotiable) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                PROJECT_ID, 1L, "페어링 웹 리뉴얼", budgetAmount,
                workStyle.name(), workForm.name(), startDesiredDate, startNegotiable);
    }

    private CreateNegotiationCommand command(Long budgetCap, FreelancerConditionSnapshot snapshot) {
        return new CreateNegotiationCommand(100L, PROJECT_ID, 10L, 51L, budgetCap, snapshot);
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
        assertThat(saved.getFreelancerId()).isEqualTo(51L);
        assertThat(saved.getConditions())
                .extracting(NegotiationCondition::getConditionType)
                .containsExactlyInAnyOrder(ConditionType.AMOUNT, ConditionType.WORK_STYLE,
                        ConditionType.WORK_FORM, ConditionType.START_DATE);
    }

    @Test
    @DisplayName("모든 조건이 맞으면 협상 없이 즉시 타결되고 채팅방이 열린다")
    void createWithNoMismatchSettlesImmediately() {
        // 프로비저닝(채팅방 개설)이 당사자 정보를 읽으므로 프로필을 실제로 심는다.
        Long clientProfileId = clientProfileRepository.save(ClientProfile.create(
                910_101L, "삼성전자", "1234567890",
                BusinessField.IT_CONTENTS_AI, EmployeeCount.SIZE_50_299)).getId();
        Long freelancerProfileId = freelancerProfileRepository.save(
                FreelancerProfile.create(910_102L, LocalDate.of(1990, 1, 1))).getId();
        jdbcTemplate.update("INSERT INTO project "
                        + "(id, client_id, title, budget_amount, work_style, work_form, start_desired_date, start_negotiable) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                PROJECT_ID, clientProfileId, "페어링 웹 리뉴얼", 5_000_000L,
                WorkStyle.REMOTE.name(), WorkForm.FULL_TIME.name(), LocalDate.of(2026, 1, 1), true);

        Long id = commandUseCase.create(new CreateNegotiationCommand(100L, PROJECT_ID, 10L, freelancerProfileId,
                5_000_000L,
                new FreelancerConditionSnapshot(PayUnit.MONTHLY, 5_000_000L,
                        WorkStyle.REMOTE, WorkForm.FULL_TIME, LocalDate.of(2026, 1, 1), false,
                        null, null, null)));

        Negotiation saved = negotiationRepository.findById(id).orElseThrow();
        assertThat(saved.getConditions()).isEmpty();
        assertThat(saved.getStatus()).isEqualTo(NegotiationStatus.AGREED);
        assertThat(saved.getAgreedAmount()).isEqualTo(5_000_000L);
        assertThat(chatRoomRepository.findByNegotiationId(id)).isPresent();
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
