package com.pairing.negotiation.application.service;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.repository.ClientProfileRepository;
import com.pairing.account.domain.repository.FreelancerProfileRepository;
import com.pairing.chat.application.usecase.ChatCommandUseCase;
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
    private ChatCommandUseCase chatCommandUseCase;
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
                BusinessField.IT_CONTENTS_AI, EmployeeCount.SIZE_50_299)).getId();
        Long freelancerProfileId = freelancerProfileRepository.save(
                FreelancerProfile.create(FREELANCER_ACCOUNT_ID, LocalDate.of(1990, 1, 1))).getId();
        jdbcTemplate.update("INSERT INTO project (id, client_id, title, start_negotiable) VALUES (?, ?, ?, ?)",
                PROJECT_ID, clientProfileId, "페어링 웹 리뉴얼", true);

        negotiationId = negotiationRepository.save(Negotiation.create(810L, PROJECT_ID, 10L,
                freelancerProfileId, 5_000_000L,
                List.of(NegotiationCondition.create(ConditionType.AMOUNT, "4000000", "6000000", 0)))).getId();
    }

    @Test
    @DisplayName("채팅방 생성 전에는 chatRoomId 가 null")
    void nullBeforeProvision() {
        NegotiationView view = queryUseCase.getDetail(negotiationId, FREELANCER_ACCOUNT_ID);
        assertThat(view.chatRoomId()).isNull();
    }

    @Test
    @DisplayName("타결 프로비저닝 후에는 상세에 채팅방 ID 가 채워진다")
    void filledAfterProvision() {
        chatCommandUseCase.provisionForAgreedNegotiation(negotiationId);
        Long roomId = chatRoomRepository.findByNegotiationId(negotiationId).orElseThrow().getId();

        NegotiationView view = queryUseCase.getDetail(negotiationId, FREELANCER_ACCOUNT_ID);
        assertThat(view.chatRoomId()).isEqualTo(roomId);
    }
}
