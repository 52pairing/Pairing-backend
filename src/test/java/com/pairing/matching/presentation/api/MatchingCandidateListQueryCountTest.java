package com.pairing.matching.presentation.api;

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
import com.pairing.freelancer.application.command.UpsertConditionCommand;
import com.pairing.freelancer.application.usecase.FreelancerConditionUseCase;
import com.pairing.global.config.SyncTaskExecutorTestConfig;
import com.pairing.global.ratelimit.RateLimitProvider;
import com.pairing.matching.application.port.out.MatchingPort;
import com.pairing.matching.domain.model.MatchingCandidate;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.RecommendationType;
import com.pairing.matching.domain.repository.MatchingCandidateRepository;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import com.pairing.matching.infrastructure.persistence.SpringDataMatchingCandidateRepository;
import com.pairing.matching.infrastructure.persistence.SpringDataMatchingRequestRepository;
import com.pairing.matching.infrastructure.persistence.SpringDataMatchingRoundRepository;
import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.SkillLevel;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.terms.domain.model.TermsCode;
import com.pairing.terms.infrastructure.persistence.SpringDataTermsAgreementRepository;
import com.pairing.terms.infrastructure.persistence.SpringDataTermsRepository;
import com.pairing.terms.infrastructure.persistence.TermsJpaEntity;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "후보 목록 카드 N+1"(6개 항목 중 3번)의 개선 전/후를 실측한다. 조건(스킬·경력·단가)은 이미
 * 스냅샷 배치로 고쳐져 있었고, 이번에 2번이 추가한 {@code FreelancerCandidateSummaryUseCase.getSummaries}
 * 를 받아 카드 요약(이름·사진·등급·평점)까지 배치로 바꿨다.
 */
@Import(SyncTaskExecutorTestConfig.class)
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
class MatchingCandidateListQueryCountTest {

    private static final String CLIENT_EMAIL = "candidate-query-client@pairing.com";
    private static final String PASSWORD = "Passw0rd!";
    private static final Long PROJECT_ID = 7_301L;
    private static final Long POSITION_ID = 7_301L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
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
    private MatchingCandidateRepository matchingCandidateRepository;
    @Autowired
    private FreelancerConditionUseCase freelancerConditionUseCase;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

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
    @MockitoBean
    private RateLimitProvider rateLimitProvider;

    private Long clientTermsId;
    private Long freelancerTermsId;
    private Long privacyTermsId;
    private Long marketingTermsId;
    private Cookie clientAccessToken;

    @BeforeEach
    void setUp() throws Exception {
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
        freelancerTermsId = saveTerms(TermsCode.SERVICE, "서비스 이용약관 동의", true, "FREELANCER");
        privacyTermsId = saveTerms(TermsCode.PRIVACY_CONSENT, "개인정보 수집 및 이용 동의", true, null);
        marketingTermsId = saveTerms(TermsCode.MARKETING, "마케팅 정보 수신 동의", false, null);

        given(verifiedMarkerPort.isVerified(anyString(), any())).willReturn(true);
        given(sessionRegistryPort.isAlive(any(), anyString())).willReturn(true);
        given(rateLimitProvider.getBucket(any(), anyString()))
                .willReturn(Bucket.builder().addLimit(Bandwidth.simple(1000, Duration.ofMinutes(1))).build());

        signUpAndLoginClient();
        Long clientProfileId = clientProfileRepository.findByAccountIdAndDeletedAtIsNull(
                accountRepository.findByEmailAndRoleAndDeletedAtIsNull(CLIENT_EMAIL,
                        com.pairing.account.domain.model.Role.CLIENT).orElseThrow().getId())
                .orElseThrow().getId();
        seedProjectWithPosition(clientProfileId);

        // 서로 다른 프리랜서 8명을 노출 후보로 심는다 — 카드 요약이 실제로 8번 개별 호출되던 자리.
        MatchingRound round = matchingRoundRepository.save(MatchingRound.create(PROJECT_ID, POSITION_ID, 1,
                RecommendationType.INITIAL, null, 0L, 8, 24));
        for (int i = 0; i < 8; i++) {
            long freelancerId = 8_301_000L + i;
            seedFreelancer(freelancerId);
            MatchingCandidate candidate = MatchingCandidate.createFromEmbedding(round.getId(), POSITION_ID,
                    freelancerId, 0.8);
            candidate.applyLlmResult(88.0, "요구 스킬 일치");
            candidate.applyGradeWeight(0.0);
            candidate.applyGuard(true, null);
            candidate.expose(i + 1);
            matchingCandidateRepository.save(candidate);
        }
    }

    private Long saveTerms(TermsCode code, String title, boolean required, String targetRole) {
        return termsRepository.save(new TermsJpaEntity(
                null, code, code.getType(), "v1.0", title, "본문", required, targetRole,
                LocalDateTime.now().minusDays(1)
        )).getId();
    }

    private void seedProjectWithPosition(Long clientProfileId) {
        jdbcTemplate.update(
                "INSERT INTO project (id, client_id, title, start_desired_date, start_negotiable, "
                        + "period_value, period_unit, budget_amount, work_style, work_form, work_location, "
                        + "current_situation, main_task, detail_scope, extra_note, "
                        + "status, payment_status, total_headcount, confirmed_headcount, "
                        + "extension_count, free_rerecommend_used, paid_rerecommend_used) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                PROJECT_ID, clientProfileId, "카드 쿼리 측정 프로젝트", LocalDate.now().plusDays(14), false,
                6, "MONTH", 60_000_000L, "ONSITE", "FULL_TIME", "서울 강남구",
                "현행 운영중", "백엔드 API 개발", "주문 도메인", "MSA 우대",
                "RECRUITING", "SUCCESS_FEE_PAID", 8, 0, 0, 0, 0);

        jdbcTemplate.update(
                "INSERT INTO project_position (id, project_id, position_no, job_category, job_role, "
                        + "min_career_years, headcount, confirmed_count, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                POSITION_ID, PROJECT_ID, 1, "DEVELOPMENT", "BACKEND", 3, 8, 0, "RECRUITING");
    }

    private void signUpAndLoginClient() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("companyName", "주식회사 페어링테크");
        body.put("businessNo", "1234567890");
        body.put("businessField", "IT_CONTENTS_AI");
        body.put("employeeCount", "SIZE_10_49");
        body.put("address", Map.of("sido", "서울", "sigungu", "강남구", "roadAddress", "서울 강남구 테헤란로 1",
                "addressDetail", "10층", "zipCode", "06234"));
        body.put("email", CLIENT_EMAIL);
        body.put("name", "김클라");
        body.put("phone", "010-1111-2222");
        body.put("password", PASSWORD);
        body.put("passwordConfirm", PASSWORD);
        body.put("card", Map.of("cardNumber", "1234-5678-1234-5678", "cardBrand", "SHINHAN"));
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
    }

    /** freelancerId == freelancer_profile.id 가 되도록 계정을 직접 심는다(회원가입 API 없이). */
    private void seedFreelancer(long freelancerId) {
        jdbcTemplate.update(
                "INSERT INTO account (id, email, role, name, phone, signup_type, status, email_verified, "
                        + "login_fail_count, is_temp_password) "
                        + "VALUES (?, ?, 'FREELANCER', '박프리', '010-9999-0002', 'EMAIL', 'ACTIVE', true, 0, false)",
                freelancerId, "freelancer-" + freelancerId + "@pairing.com");
        jdbcTemplate.update(
                "INSERT INTO freelancer_profile (id, account_id, birth_date, ai_matching_agreed, grade) "
                        + "VALUES (?, ?, ?, true, 'JUNIOR')",
                freelancerId, freelancerId, LocalDate.of(1998, 5, 5));
        freelancerConditionUseCase.upsert(new UpsertConditionCommand(
                freelancerId, JobCategory.DEVELOPMENT, JobRole.BACKEND,
                WorkStyle.REMOTE, WorkForm.FULL_TIME, PayUnit.MONTHLY, 4_000_000L, 3_500_000L,
                LocalDate.now().plusDays(14), false, 6, PeriodUnit.MONTH, true, 2,
                List.of(new UpsertConditionCommand.Skill(SkillCode.SPRING_BOOT, SkillLevel.ADVANCED))));
    }

    @Test
    void printsQueryCountForCandidateList() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/v1/matchings/positions/" + POSITION_ID + "/candidates")
                        .cookie(clientAccessToken))
                .andExpect(status().isOk());

        long queryCount = statistics.getPrepareStatementCount();
        System.out.println("=====CANDIDATE_QUERY_COUNT===== GET /candidates (노출 8명) -> "
                + queryCount + " prepared statements =====CANDIDATE_QUERY_COUNT=====");
    }
}
