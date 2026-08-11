package com.pairing.review.presentation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.account.domain.model.AccountStatus;
import com.pairing.account.domain.model.Role;
import com.pairing.account.domain.model.SignupType;
import com.pairing.account.infrastructure.persistence.AccountJpaEntity;
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
import com.pairing.review.infrastructure.persistence.SpringDataReviewRepository;
import com.pairing.review.infrastructure.persistence.SpringDataSiteReviewRepository;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 리뷰 작성 -> 받은/작성한 목록 -> 요약, 관리자 사이트 리뷰 흐름을 실제 요청으로 확인한다.
 *
 * <p>contract/settlement 도메인이 아직 스켈레톤이라 projectId/revieweeAccountId 를 요청에서
 * 직접 받는 임시 계약을 그대로 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewIntegrationTest {

    private static final String CLIENT_EMAIL = "review-client@pairing.com";
    private static final String FREELANCER_EMAIL = "review-freelancer@pairing.com";
    private static final String PASSWORD = "Passw0rd!";
    private static final Long CONTRACT_ID = 999L;

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
    private SpringDataReviewRepository reviewRepository;
    @Autowired
    private SpringDataSiteReviewRepository siteReviewRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final Long PROJECT_ID = 5001L;
    private static final String ADMIN_EMAIL = "review-admin@pairing.com";

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

    private Long clientTermsId;
    private Long freelancerTermsId;
    private Long privacyTermsId;
    private Long marketingTermsId;
    private Cookie clientAccessToken;
    private Cookie freelancerAccessToken;
    private Long clientAccountId;
    private Long freelancerAccountId;
    private Long projectId;

    @BeforeEach
    void setUp() throws Exception {
        siteReviewRepository.deleteAll();
        reviewRepository.deleteAll();
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

        signUpAndLoginClient();
        signUpAndLoginFreelancer();

        Long clientProfileId = clientProfileRepository.findByAccountIdAndDeletedAtIsNull(clientAccountId)
                .orElseThrow().getId();
        projectId = seedProject(clientProfileId);
    }

    private Long saveTerms(TermsCode code, String title, boolean required, String targetRole) {
        return termsRepository.save(new TermsJpaEntity(
                null, code, code.getType(), "v1.0", title, "본문", required, targetRole,
                LocalDateTime.now().minusDays(1)
        )).getId();
    }

    /**
     * {@code ProjectRepository.save()} (JPA IDENTITY 채번)를 쓰지 않고 명시적 id로 직접 넣는다.
     * negotiation 의 {@code ProjectReadJpaEntity} 가 같은 project 테이블을 {@code @GeneratedValue} 없이
     * 매핑하고 있어, ddl-auto=update 로 만들어진 H2 스키마에서 id 컬럼에 identity 가 안 걸리는 충돌이 있다
     * (두 엔티티가 한 테이블을 서로 다른 채번 전략으로 매핑). Review 테스트 목적상 이 프로젝트 id 충돌은
     * 우회하고 명시적 id로 심는다.
     */
    private Long seedProject(Long clientProfileId) {
        jdbcTemplate.update(
                "INSERT INTO project (id, client_id, title, start_negotiable, period_value, period_unit, "
                        + "budget_amount, work_style, work_form, current_situation, main_task, status, "
                        + "payment_status, total_headcount, confirmed_headcount, extension_count, "
                        + "free_rerecommend_used, paid_rerecommend_used) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                PROJECT_ID, clientProfileId, "페어링 웹 리뉴얼", true, 6, "MONTH",
                22_000_000L, "REMOTE", "FULL_TIME", "현행 사이트 운영중", "백엔드 개발", "CLOSED",
                "SUCCESS_FEE_PAID", 1, 1, 0, 0, 0);
        return PROJECT_ID;
    }

    private void signUpAndLoginClient() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("companyName", "주식회사 페어링");
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

    private void signUpAndLoginFreelancer() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "이프리");
        body.put("phone", "010-3333-4444");
        body.put("email", FREELANCER_EMAIL);
        body.put("password", PASSWORD);
        body.put("passwordConfirm", PASSWORD);
        body.put("birthDate", "1995-03-01");
        body.put("card", Map.of("cardNumber", "1234-5678-1234-5678", "cardBrand", "신한카드"));
        body.put("bankAccount", Map.of("bankCode", "088", "accountNo", "110-123-456789", "accountHolder", "이프리"));
        body.put("agreements", List.of(
                Map.of("termsId", freelancerTermsId, "agreed", true),
                Map.of("termsId", privacyTermsId, "agreed", true),
                Map.of("termsId", marketingTermsId, "agreed", true)));

        mockMvc.perform(post("/api/v1/auth/signup/freelancer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","role":"FREELANCER"}"""
                                .formatted(FREELANCER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        freelancerAccessToken = loginResult.getResponse().getCookie("accessToken");
        freelancerAccountId = accountRepository.findByEmailAndRoleAndDeletedAtIsNull(FREELANCER_EMAIL,
                com.pairing.account.domain.model.Role.FREELANCER).orElseThrow().getId();
    }

    /** 관리자는 공개 가입이 없어 계정을 직접 심고 일반 로그인으로 토큰을 받는다. */
    private Cookie loginAsAdmin() throws Exception {
        accountRepository.save(new AccountJpaEntity(null, ADMIN_EMAIL, passwordEncoder.encode(PASSWORD),
                Role.ADMIN, "관리자", "010-9999-0000", SignupType.EMAIL, AccountStatus.ACTIVE, true, 0,
                null, false, null, null, null, null, null, null, null, null, null, null));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","role":"ADMIN"}"""
                                .formatted(ADMIN_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        return loginResult.getResponse().getCookie("accessToken");
    }

    private Map<String, Object> reviewCreateBody(Long revieweeAccountId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contractId", CONTRACT_ID);
        body.put("projectId", projectId);
        body.put("revieweeAccountId", revieweeAccountId);
        body.put("counterpart", Map.of("score", 5, "content", "일정 준수가 좋았습니다."));
        body.put("site", Map.of("score", 4, "content", "협상 과정이 편했습니다."));
        return body;
    }

    @Test
    @DisplayName("프리랜서가 클라이언트에게 리뷰를 쓰면 받은/작성한 목록과 요약에 반영된다")
    void createReviewReflectsInListsAndSummary() throws Exception {
        mockMvc.perform(post("/api/v1/reviews")
                        .cookie(freelancerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewCreateBody(clientAccountId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reviewerName").value("이프리"))
                .andExpect(jsonPath("$.data.reviewerRole").value("FREELANCER"))
                .andExpect(jsonPath("$.data.score").value(5))
                .andExpect(jsonPath("$.data.projectTitle").value("페어링 웹 리뉴얼"));

        mockMvc.perform(get("/api/v1/reviews/written").cookie(freelancerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].score").value(5));

        mockMvc.perform(get("/api/v1/reviews/received").cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].reviewerName").value("이프리"));

        mockMvc.perform(get("/api/v1/reviews/summary").cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageScore").value(5.0))
                .andExpect(jsonPath("$.data.reviewCount").value(1))
                .andExpect(jsonPath("$.data.grade").value("SILVER"));
    }

    @Test
    @DisplayName("없는 계정을 리뷰 대상으로 넣으면 저장 전에 404로 막는다")
    void createReviewWithUnknownRevieweeIsRejected() throws Exception {
        // 검증이 없으면 저장 단계에서야 터져 500(GLOBAL_001)이 나간다.
        mockMvc.perform(post("/api/v1/reviews")
                        .cookie(freelancerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewCreateBody(999_999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("AC_001"));
    }

    @Test
    @DisplayName("같은 계약을 같은 사람이 두 번 리뷰하면 막힌다")
    void duplicateReviewIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/reviews")
                        .cookie(freelancerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewCreateBody(clientAccountId))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/reviews")
                        .cookie(freelancerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewCreateBody(clientAccountId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RV_001"));
    }

    @Test
    @DisplayName("작성 대기 목록은 아직 항상 빈 목록이다")
    void pendingReviewsIsAlwaysEmptyForNow() throws Exception {
        mockMvc.perform(get("/api/v1/reviews/pending").cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("사이트 리뷰는 기본 비공개이고, 관리자가 공개·홍보로 바꾸면 요약에 반영된다")
    void adminCanChangeSiteReviewVisibility() throws Exception {
        mockMvc.perform(post("/api/v1/reviews")
                        .cookie(freelancerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewCreateBody(clientAccountId))))
                .andExpect(status().isCreated());

        Cookie adminAccessToken = loginAsAdmin();
        Long siteReviewId = siteReviewRepository.findAll().get(0).getId();

        mockMvc.perform(get("/api/v1/reviews/admin/site-reviews/summary").cookie(adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.publicCount").value(0))
                .andExpect(jsonPath("$.data.promotedCount").value(0));

        mockMvc.perform(get("/api/v1/reviews/admin/site-reviews").cookie(adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].visibility").value("PRIVATE"));

        mockMvc.perform(put("/api/v1/reviews/admin/site-reviews/" + siteReviewId + "/visibility")
                        .cookie(adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"visibility":"PUBLIC","promoted":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.promoted").value(true));

        mockMvc.perform(get("/api/v1/reviews/admin/site-reviews/summary").cookie(adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicCount").value(1))
                .andExpect(jsonPath("$.data.promotedCount").value(1));
    }
}
