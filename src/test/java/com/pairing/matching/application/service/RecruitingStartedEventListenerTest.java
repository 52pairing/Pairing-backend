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
import com.pairing.matching.application.result.MatchingRecommendation;
import com.pairing.matching.application.result.RankedFreelancer;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.RecommendationType;
import com.pairing.matching.domain.model.SnapshotType;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import com.pairing.matching.domain.repository.MatchingSnapshotRepository;
import com.pairing.matching.infrastructure.persistence.SpringDataMatchingCandidateRepository;
import com.pairing.matching.infrastructure.persistence.SpringDataMatchingRequestRepository;
import com.pairing.matching.infrastructure.persistence.SpringDataMatchingRoundRepository;
import com.pairing.project.application.event.RecruitingStartedEvent;
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
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * project 도메인이 발행하는 {@link RecruitingStartedEvent}를 받아 스냅샷 동결 -&gt; 임베딩
 * upsert -&gt; 최초 추천 라운드 생성까지 이어지는지 확인한다.
 *
 * <p>{@code @TransactionalEventListener(AFTER_COMMIT)}는 실제로 트랜잭션이 커밋돼야 실행되므로,
 * 이벤트 발행을 {@link TransactionTemplate}로 감싸 실제 커밋을 일으킨 뒤 결과를 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecruitingStartedEventListenerTest {

    private static final String CLIENT_EMAIL = "recruiting-client@pairing.com";
    private static final String PASSWORD = "Passw0rd!";
    private static final Long PROJECT_ID = 7101L;
    private static final Long POSITION_ID = 7101L;

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
    private SpringDataMatchingRoundRepository matchingRoundJpaRepository;
    @Autowired
    private SpringDataMatchingCandidateRepository matchingCandidateJpaRepository;
    @Autowired
    private SpringDataMatchingRequestRepository matchingRequestJpaRepository;
    @Autowired
    private MatchingRoundRepository matchingRoundRepository;
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
        jdbcTemplate.update("DELETE FROM negotiation_message WHERE negotiation_id IN "
                + "(SELECT id FROM negotiation WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM negotiation_condition WHERE negotiation_id IN "
                + "(SELECT id FROM negotiation WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM negotiation WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM matching_snapshot WHERE project_id = ?", PROJECT_ID);
        matchingRequestJpaRepository.deleteAll();
        matchingCandidateJpaRepository.deleteAll();
        matchingRoundJpaRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM position_skill WHERE position_id = ?", POSITION_ID);
        jdbcTemplate.update("DELETE FROM project_position WHERE id = ?", POSITION_ID);
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
        seedProjectWithPosition(clientProfileId);
    }

    private Long saveTerms(TermsCode code, String title, boolean required, String targetRole) {
        return termsRepository.save(new TermsJpaEntity(
                null, code, code.getType(), "v1.0", title, "본문", required, targetRole,
                java.time.LocalDateTime.now().minusDays(1)
        )).getId();
    }

    private void seedProjectWithPosition(Long clientProfileId) {
        jdbcTemplate.update(
                "INSERT INTO project (id, client_id, title, start_desired_date, start_negotiable, "
                        + "period_value, period_unit, budget_amount, work_style, work_form, current_situation, "
                        + "main_task, status, payment_status, total_headcount, confirmed_headcount, "
                        + "extension_count, free_rerecommend_used, paid_rerecommend_used) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                PROJECT_ID, clientProfileId, "AI 추천 시스템 구축", LocalDate.now().plusDays(14), false,
                6, "MONTH", 60_000_000L, "ONSITE", "FULL_TIME", "현행 시스템 운영중", "백엔드 API 개발",
                "RECRUITING", "SUCCESS_FEE_PAID", 2, 0, 0, 0, 0);

        jdbcTemplate.update(
                "INSERT INTO project_position (id, project_id, position_no, job_category, job_role, "
                        + "min_career_years, headcount, confirmed_count, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                POSITION_ID, PROJECT_ID, 1, "DEVELOPMENT", "BACKEND", 3, 2, 0, "RECRUITING");

        jdbcTemplate.update(
                "INSERT INTO position_skill (position_id, skill_code) VALUES (?, ?)",
                POSITION_ID, "SPRING_BOOT");
    }

    private void signUpAndLoginClient() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("companyName", "주식회사 페어링테크");
        body.put("businessNo", "1234567890");
        body.put("businessField", "IT_CONTENTS_AI");
        body.put("employeeCount", "SIZE_10_49");
        body.put("address", "서울 강남구 테헤란로 1");
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
    @DisplayName("모집 시작 이벤트를 받으면 스냅샷을 얼리고 임베딩을 올리고 최초 추천 라운드를 만든다")
    void recruitingStartedEventCreatesSnapshotEmbeddingAndInitialRound() {
        given(matchingPort.recommend(eq(POSITION_ID), eq(2), eq(3)))
                .willReturn(new MatchingRecommendation(POSITION_ID, "gemini-2.0-flash",
                        List.of(new RankedFreelancer(999_001L, 90.0, "요구 스킬 일치|경력 조건 충족"))));

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventPublisher.publishEvent(new RecruitingStartedEvent(PROJECT_ID)));

        MatchingRound round = matchingRoundRepository.findLatestByPositionId(POSITION_ID).orElseThrow();
        assertThat(round.getRoundType()).isEqualTo(RecommendationType.INITIAL);
        assertThat(round.getExposeCount()).isEqualTo(2);

        assertThat(matchingSnapshotRepository.findByPositionIdAndSnapshotType(POSITION_ID, SnapshotType.PROJECT))
                .isPresent();
        assertThat(matchingSnapshotRepository.findByPositionIdAndSnapshotType(POSITION_ID, SnapshotType.POSITION))
                .isPresent();

        verify(matchingPort).upsertPositionEmbedding(eq(POSITION_ID), anyString());
    }

    @Test
    @DisplayName("이미 라운드가 있는 포지션은 이벤트를 다시 받아도 새 라운드를 또 만들지 않는다(멱등)")
    void recruitingStartedEventIsIdempotentPerPosition() {
        given(matchingPort.recommend(eq(POSITION_ID), eq(2), eq(3)))
                .willReturn(new MatchingRecommendation(POSITION_ID, "gemini-2.0-flash",
                        List.of(new RankedFreelancer(999_001L, 90.0, "요구 스킬 일치"))));

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventPublisher.publishEvent(new RecruitingStartedEvent(PROJECT_ID)));
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventPublisher.publishEvent(new RecruitingStartedEvent(PROJECT_ID)));

        long roundCount = matchingRoundJpaRepository.count();
        assertThat(roundCount).isEqualTo(1);
    }
}
