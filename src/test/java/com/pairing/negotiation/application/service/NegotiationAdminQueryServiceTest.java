package com.pairing.negotiation.application.service;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.repository.ClientProfileRepository;
import com.pairing.account.domain.repository.FreelancerProfileRepository;
import com.pairing.global.exception.BusinessException;
import com.pairing.negotiation.application.result.admin.AdminDetail;
import com.pairing.negotiation.application.result.admin.AdminListItem;
import com.pairing.negotiation.application.result.admin.AdminSummary;
import com.pairing.negotiation.application.usecase.NegotiationAdminQueryUseCase;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.model.NegotiationMessage;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.domain.model.SenderType;
import com.pairing.negotiation.domain.repository.NegotiationMessageRepository;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import com.pairing.negotiation.exception.NegotiationErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 관리자 협상 조회: 요약 집계 / 키워드·상태 검색 / 상세(최종결과·라운드로그) / 원본로그 / 로그. */
@SpringBootTest
@Transactional
class NegotiationAdminQueryServiceTest {

    @Autowired
    private NegotiationAdminQueryUseCase adminUseCase;
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
    @PersistenceContext
    private EntityManager entityManager;

    private static final Long CLIENT_ACCOUNT_ID = 930_001L;
    private static final Long FREELANCER_ACCOUNT_ID = 930_002L;
    private static final Long PROJECT_ID = 8400L;

    private Long negotiationId;
    private Long amountConditionId;

    @BeforeEach
    void setUp() {
        Long clientProfileId = clientProfileRepository.save(ClientProfile.create(
                CLIENT_ACCOUNT_ID, "삼성전자", "1234567890",
                BusinessField.IT_CONTENTS_AI, EmployeeCount.SIZE_50_299)).getId();
        Long freelancerProfileId = freelancerProfileRepository.save(
                FreelancerProfile.create(FREELANCER_ACCOUNT_ID, java.time.LocalDate.of(1990, 1, 1))).getId();
        jdbcTemplate.update("INSERT INTO project (id, client_id, title, start_negotiable) VALUES (?, ?, ?, ?)",
                PROJECT_ID, clientProfileId, "페어링 웹 리뉴얼", true);

        // 타결된 협상 1건 + 라운드1 제안·응답 로그.
        Negotiation saved = negotiationRepository.save(Negotiation.create(800L, PROJECT_ID, 10L,
                freelancerProfileId, 5_000_000L,
                List.of(NegotiationCondition.create(ConditionType.AMOUNT, "4000000", "6000000", 0))));
        negotiationId = saved.getId();
        amountConditionId = saved.getConditions().get(0).getId();

        messageRepository.saveAll(List.of(
                NegotiationMessage.proposal(negotiationId, amountConditionId, 1, SenderType.SYSTEM,
                        "월 500만원 제안", "중간값", "5000000"),
                NegotiationMessage.response(negotiationId, amountConditionId, 1, SenderType.FREELANCER,
                        "제안을 수락했습니다.", "YES", FREELANCER_ACCOUNT_ID)));

        saved.getConditions().get(0).lock("5000000");
        saved.agree(5_000_000L);
        negotiationRepository.save(saved);

        // JDBC 리더(요약·검색)는 세션을 우회해 DB 를 직접 읽으므로, 트랜잭션 내 변경을 먼저 flush 한다.
        // (운영에선 협상 생성/타결이 별도 요청으로 이미 커밋된 상태라 이 문제가 없다.)
        entityManager.flush();
    }

    @Test
    @DisplayName("요약: 상태별 집계(타결 1건)")
    void summaryAggregates() {
        AdminSummary summary = adminUseCase.getSummary();
        assertThat(summary.total()).isEqualTo(1);
        assertThat(summary.agreed()).isEqualTo(1);
        assertThat(summary.inProgress()).isZero();
        assertThat(summary.failed()).isZero();
    }

    @Test
    @DisplayName("검색: 프로젝트명 키워드로 찾고 클라이언트명을 채운다")
    void searchByKeyword() {
        var page = adminUseCase.search("페어링", null, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
        AdminListItem item = page.getContent().get(0);
        assertThat(item.projectTitle()).isEqualTo("페어링 웹 리뉴얼");
        assertThat(item.clientName()).isEqualTo("삼성전자");
        assertThat(item.status()).isEqualTo(NegotiationStatus.AGREED);
    }

    @Test
    @DisplayName("검색: 매칭 없는 키워드는 빈 페이지")
    void searchNoMatch() {
        assertThat(adminUseCase.search("존재하지않는프로젝트", null, PageRequest.of(0, 10)).getTotalElements())
                .isZero();
    }

    @Test
    @DisplayName("상세: 최종결과 금액 라벨 + 라운드 로그 조립")
    void detailAssemblesFinalResultAndLogs() {
        AdminDetail detail = adminUseCase.getDetail(negotiationId);
        assertThat(detail.status()).isEqualTo(NegotiationStatus.AGREED);
        assertThat(detail.clientName()).isEqualTo("삼성전자");
        assertThat(detail.finalResult()).isNotNull();
        assertThat(detail.finalResult().amountLabel()).isEqualTo("월 5,000,000원");
        // 시스템 메시지는 제외, 제안·응답만. 제안 로그에 금액 라벨이 붙는다.
        assertThat(detail.roundLogs()).hasSize(2);
        assertThat(detail.roundLogs()).anyMatch(r -> "월 5,000,000원".equals(r.proposedAmountLabel()));
    }

    @Test
    @DisplayName("로그: 메시지 + 조건 타입 매핑 반환")
    void messagesWithTypeMap() {
        NegotiationAdminQueryUseCase.AdminMessages result = adminUseCase.getMessages(negotiationId);
        assertThat(result.messages()).hasSize(2);
        assertThat(result.conditionTypes()).containsEntry(amountConditionId, ConditionType.AMOUNT);
    }

    @Test
    @DisplayName("원본 로그: ai_agent_log 미기록/부재 시 빈 리스트(예외 없음)")
    void rawLogsGracefulWhenEmpty() {
        assertThat(adminUseCase.getRawLogs(negotiationId)).isEmpty();
    }

    @Test
    @DisplayName("상세: 없는 협상은 NG_001")
    void detailNotFound() {
        assertThatThrownBy(() -> adminUseCase.getDetail(999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(NegotiationErrorCode.NEGOTIATION_NOT_FOUND);
    }
}
