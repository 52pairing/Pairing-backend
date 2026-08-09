package com.pairing.matching.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.account.infrastructure.persistence.SpringDataAccountRepository;
import com.pairing.account.infrastructure.persistence.SpringDataClientProfileRepository;
import com.pairing.account.infrastructure.persistence.SpringDataFreelancerProfileRepository;
import com.pairing.account.infrastructure.persistence.SpringDataPaymentMethodRepository;
import com.pairing.account.infrastructure.persistence.SpringDataSocialAccountRepository;
import com.pairing.auth.application.port.AccountSuspensionPort;
import com.pairing.auth.application.port.EmailSendLimitPort;
import com.pairing.auth.application.port.LoginAttemptPort;
import com.pairing.auth.application.port.MailSenderPort;
import com.pairing.auth.application.port.OAuthStatePort;
import com.pairing.auth.application.port.PasswordResetTokenPort;
import com.pairing.auth.application.port.SessionRegistryPort;
import com.pairing.auth.application.port.SignUpTicketPort;
import com.pairing.auth.application.port.TokenStorePort;
import com.pairing.auth.application.port.VerifiedMarkerPort;
import com.pairing.matching.application.port.out.MatchingPort;
import com.pairing.matching.domain.model.MatchingSnapshot;
import com.pairing.matching.domain.model.SnapshotType;
import com.pairing.matching.domain.repository.MatchingSnapshotRepository;
import com.pairing.project.application.event.ProjectUpdatedEvent;
import com.pairing.terms.domain.model.TermsCode;
import com.pairing.terms.infrastructure.persistence.SpringDataTermsAgreementRepository;
import com.pairing.terms.infrastructure.persistence.SpringDataTermsRepository;
import com.pairing.terms.infrastructure.persistence.TermsJpaEntity;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * project 도메인이 발행하는 {@link ProjectUpdatedEvent}를 받아 포지션별 임베딩을 다시 올리는지,
 * 그리고 매칭 요청 카드용 {@link MatchingSnapshot}은 건드리지 않는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectUpdatedEventListenerTest {

    private static final String CLIENT_EMAIL = "project-updated-client@pairing.com";
    private static final String PASSWORD = "Passw0rd!";
    private static final Long PROJECT_ID = 7201L;
    private static final Long POSITION_ID_1 = 7201L;
    private static final Long POSITION_ID_2 = 7202L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private SpringDataTermsRepository termsRepository;
    @Autowired
    private SpringDataTermsAgreementRepository termsAgreementRepository;
    @Autowired
    private SpringDataAccountRepository accountRepository;
    @Autowired
    private SpringDataClientProfileRepository clientProfileRepository;
    @Autowired
    private SpringDataFreelancerProfileRepository freelancerProfileRepository;
    @Autowired
    private SpringDataPaymentMethodRepository paymentMethodRepository;
    @Autowired
    private SpringDataSocialAccountRepository socialAccountRepository;
    @Autowired
    private MatchingSnapshotRepository matchingSnapshotRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private VerifiedMarkerPort verifiedMarkerPort;
    @MockitoBean
    private TokenStorePort tokenStorePort;
    @MockitoBean
    private SessionRegistryPort sessionRegistryPort;
    @MockitoBean
    private LoginAttemptPort loginAttemptPort;
    @MockitoBean
    private AccountSuspensionPort accountSuspensionPort;
    @MockitoBean
    private EmailSendLimitPort emailSendLimitPort;
    @MockitoBean
    private MailSenderPort mailSenderPort;
    @MockitoBean
    private SignUpTicketPort signUpTicketPort;
    @MockitoBean
    private OAuthStatePort oAuthStatePort;
    @MockitoBean
    private PasswordResetTokenPort passwordResetTokenPort;
    @MockitoBean
    private MatchingPort matchingPort;

    private Long clientTermsId;
    private Long privacyTermsId;
    private Long marketingTermsId;
    private Cookie clientAccessToken;
    private Long clientAccountId;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM matching_snapshot WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM position_skill WHERE position_id IN (?, ?)",
                POSITION_ID_1, POSITION_ID_2);
        jdbcTemplate.update("DELETE FROM project_position WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM project WHERE id = ?", PROJECT_ID);
        termsAgreementRepository.deleteAll();
        paymentMethodRepository.deleteAll();
        clientProfileRepository.deleteAll();
        freelancerProfileRepository.deleteAll();
        socialAccountRepository.deleteAll();
        accountRepository.deleteAll();
        termsRepository.deleteAll();

        clientTermsId = saveTerms(TermsCode.SERVICE, "서비스 이용약관 동의", true, "CLIENT");
        privacyTermsId = saveTerms(TermsCode.PRIVACY_CONSENT, "개인정보 수집 및 이용 동의", true, null);
        marketingTermsId = saveTerms(TermsCode.MARKETING, "마케팅 정보 수신 동의", false, null);

        given(verifiedMarkerPort.isVerified(anyString(), any())).willReturn(true);
        given(sessionRegistryPort.isAlive(any(), anyString())).willReturn(true);

        signUpAndLoginClient();

        Long clientProfileId = clientProfileRepository.findByAccountIdAndDeletedAtIsNull(clientAccountId)
                .orElseThrow().getId();
        seedProjectWithTwoPositions(clientProfileId);
        seedSnapshotsForBothPositions();
    }

    private Long saveTerms(TermsCode code, String title, boolean required, String targetRole) {
        return termsRepository.save(new TermsJpaEntity(
                null, code, code.getType(), "v1.0", title, "본문", required, targetRole,
                java.time.LocalDateTime.now().minusDays(1)
        )).getId();
    }

    private void seedProjectWithTwoPositions(Long clientProfileId) {
        jdbcTemplate.update(
                "INSERT INTO project (id, client_id, title, start_desired_date, start_negotiable, "
                        + "period_value, period_unit, budget_amount, work_style, work_form, current_situation, "
                        + "main_task, status, payment_status, total_headcount, confirmed_headcount, "
                        + "extension_count, free_rerecommend_used, paid_rerecommend_used) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                PROJECT_ID, clientProfileId, "AI 추천 시스템 구축", LocalDate.now().plusDays(14), false,
                6, "MONTH", 60_000_000L, "ONSITE", "FULL_TIME", "현행 시스템 운영중", "백엔드 API 개발",
                "RECRUITING", "SUCCESS_FEE_PAID", 4, 0, 0, 0, 0);

        jdbcTemplate.update(
                "INSERT INTO project_position (id, project_id, position_no, job_category, job_role, "
                        + "min_career_years, headcount, confirmed_count, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                POSITION_ID_1, PROJECT_ID, 1, "DEVELOPMENT", "BACKEND", 3, 2, 0, "RECRUITING");
        jdbcTemplate.update(
                "INSERT INTO position_skill (position_id, skill_code) VALUES (?, ?)",
                POSITION_ID_1, "SPRING_BOOT");

        jdbcTemplate.update(
                "INSERT INTO project_position (id, project_id, position_no, job_category, job_role, "
                        + "min_career_years, headcount, confirmed_count, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                POSITION_ID_2, PROJECT_ID, 2, "DEVELOPMENT", "FRONTEND", 2, 1, 0, "RECRUITING");
        jdbcTemplate.update(
                "INSERT INTO position_skill (position_id, skill_code) VALUES (?, ?)",
                POSITION_ID_2, "REACT");
    }

    /** 이미 모집이 시작된 프로젝트라는 전제(스냅샷 존재) — 이 리스너는 스냅샷을 절대 건드리면 안 된다. */
    private void seedSnapshotsForBothPositions() throws Exception {
        for (Long positionId : List.of(POSITION_ID_1, POSITION_ID_2)) {
            Map<String, Object> payload = Map.of("title", "수정 전 제목");
            matchingSnapshotRepository.save(MatchingSnapshot.create(PROJECT_ID, positionId, null,
                    SnapshotType.PROJECT, objectMapper.writeValueAsString(payload)));
        }
    }

    private void signUpAndLoginClient() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("companyName", "주식회사 페어링테크");
        body.put("businessNo", "1234567890");
        body.put("businessField", "IT_CONTENTS_AI");
        body.put("employeeCount", "SIZE_10_49");
        body.put("email", CLIENT_EMAIL);
        body.put("name", "김클라");
        body.put("phone", "010-1111-2222");
        body.put("password", PASSWORD);
        body.put("passwordConfirm", PASSWORD);
        body.put("card", Map.of("cardNumber", "1234-5678-1234-5678", "cardBrand", "신한카드"));
        body.put("bankAccount", Map.of("bankCode", "088", "accountNo", "110-123-456789", "accountHolder", "김클라"));
        body.put("agreements", List.of(
                Map.of("termsId", clientTermsId, "agreed", true),
                Map.of("termsId", privacyTermsId, "agreed", true),
                Map.of("termsId", marketingTermsId, "agreed", false)));

        mockMvc.perform(post("/api/v1/auth/signup/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","role":"CLIENT"}"""
                                .formatted(CLIENT_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        clientAccessToken = loginResult.getResponse().getCookie("accessToken");
        clientAccountId = accountRepository.findByEmailAndRoleAndDeletedAtIsNull(CLIENT_EMAIL,
                com.pairing.account.domain.model.Role.CLIENT).orElseThrow().getId();
    }

    @Test
    @DisplayName("프로젝트 수정 이벤트를 받으면 프로젝트의 포지션마다 임베딩을 다시 올린다")
    void projectUpdatedEventRefreshesEmbeddingForEveryPosition() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventPublisher.publishEvent(new ProjectUpdatedEvent(PROJECT_ID)));

        verify(matchingPort).upsertPositionEmbedding(eq(POSITION_ID_1), anyString());
        verify(matchingPort).upsertPositionEmbedding(eq(POSITION_ID_2), anyString());
    }

    @Test
    @DisplayName("한 포지션의 임베딩 갱신이 실패해도 다른 포지션은 계속 처리된다")
    void oneFailingPositionDoesNotBlockOthers() {
        willThrow(new RuntimeException("AI 서버 호출 실패"))
                .given(matchingPort).upsertPositionEmbedding(eq(POSITION_ID_1), anyString());

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventPublisher.publishEvent(new ProjectUpdatedEvent(PROJECT_ID)));

        verify(matchingPort).upsertPositionEmbedding(eq(POSITION_ID_1), anyString());
        verify(matchingPort).upsertPositionEmbedding(eq(POSITION_ID_2), anyString());
    }

    @Test
    @DisplayName("매칭 요청 카드용 스냅샷은 건드리지 않는다")
    void doesNotTouchMatchingSnapshot() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventPublisher.publishEvent(new ProjectUpdatedEvent(PROJECT_ID)));

        MatchingSnapshot snapshot = matchingSnapshotRepository
                .findByPositionIdAndSnapshotType(POSITION_ID_1, SnapshotType.PROJECT)
                .orElseThrow();
        assertThat(snapshot.getSnapshotJson()).contains("수정 전 제목");
    }
}
