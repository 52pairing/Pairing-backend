package com.pairing.negotiation.application.service;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.repository.ClientProfileRepository;
import com.pairing.account.domain.repository.FreelancerProfileRepository;
import com.pairing.chat.application.usecase.ChatActivationUseCase;
import com.pairing.chat.domain.repository.ChatRoomRepository;
import com.pairing.negotiation.application.result.NegotiationView;
import com.pairing.negotiation.application.usecase.NegotiationQueryUseCase;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
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

/** 협상 상세의 chatRoomId 연동: 채팅방 생성 전 null → 타결 프로비저닝 후 방 ID 노출. */
@SpringBootTest
@Transactional
class NegotiationChatRoomLinkTest {

    @Autowired
    private NegotiationQueryUseCase queryUseCase;
    @Autowired
    private ChatActivationUseCase chatActivationUseCase;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private NegotiationRepository negotiationRepository;
    @Autowired
    private ClientProfileRepository clientProfileRepository;
    @Autowired
    private FreelancerProfileRepository freelancerProfileRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long CLIENT_ACCOUNT_ID = 950_001L;
    private static final Long FREELANCER_ACCOUNT_ID = 950_002L;
    private static final Long PROJECT_ID = 8500L;

    private Long negotiationId;

    @BeforeEach
    void setUp() {
        Long clientProfileId = clientProfileRepository.save(ClientProfile.create(
                CLIENT_ACCOUNT_ID, "삼성전자", "1234567890",
                BusinessField.IT_CONTENTS_AI, EmployeeCount.SIZE_50_299, "서울 강남구 테헤란로 1")).getId();
        Long freelancerProfileId = freelancerProfileRepository.save(
                FreelancerProfile.create(FREELANCER_ACCOUNT_ID, LocalDate.of(1990, 1, 1))).getId();
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

        negotiationId = negotiationRepository.save(Negotiation.create(810L, PROJECT_ID, 10L,
                freelancerProfileId, 5_000_000L, 5_000_000L,
                List.of(NegotiationCondition.create(ConditionType.AMOUNT, "4000000", "6000000", 0)))).getId();
    }

    @Test
    @DisplayName("계약 체결 전에는 chatRoomId 가 null")
    void nullBeforeProvision() {
        NegotiationView view = queryUseCase.getDetail(negotiationId, FREELANCER_ACCOUNT_ID);
        assertThat(view.chatRoomId()).isNull();
    }

    @Test
    @DisplayName("계약 체결로 방이 열리면 상세에 채팅방 ID 가 채워진다")
    void filledAfterProvision() {
        chatActivationUseCase.openForSignedContract(negotiationId);
        Long roomId = chatRoomRepository.findByNegotiationId(negotiationId).orElseThrow().getId();

        NegotiationView view = queryUseCase.getDetail(negotiationId, FREELANCER_ACCOUNT_ID);
        assertThat(view.chatRoomId()).isEqualTo(roomId);
    }
}
