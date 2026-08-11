package com.pairing.home.presentation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.contract.application.result.ContractDetail;
import com.pairing.contract.application.usecase.ContractQueryUseCase;
import com.pairing.global.config.ContractDetailStub;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 비로그인 메인 노출 리뷰(/api/v1/home/site-reviews)를 실제 요청으로 확인한다.
 *
 * <p>로그인 쿠키 없이 호출해서 실제로 공개돼 있는지, 공개+홍보활용+4점 이상만 노출되는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HomeIntegrationTest {

    private static final String CLIENT_EMAIL = "home-client@pairing.com";
    private static final String FREELANCER_EMAIL = "home-freelancer@pairing.com";
    private static final String LOW_SCORE_FREELANCER_EMAIL = "home-freelancer-low@pairing.com";
    private static final String ADMIN_EMAIL = "home-admin@pairing.com";
    private static final String PASSWORD = "Passw0rd!";
    private static final Long PROJECT_ID = 5301L;

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

    // 리뷰를 만들려면 계약이 있어야 한다. 계약 생성 플로우까지 태우지 않고 조회만 대신한다.
    @MockitoBean
    private ContractQueryUseCase contractQueryUseCase;

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
    private Cookie lowScoreFreelancerAccessToken;
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

        clientAccessToken = signUpAndLoginClient();
        freelancerAccessToken = signUpAndLoginFreelancer(FREELANCER_EMAIL, "이프리", "010-3333-4444",
                "110-123-456789");
        lowScoreFreelancerAccessToken = signUpAndLoginFreelancer(LOW_SCORE_FREELANCER_EMAIL, "박낮음",
                "010-5555-6666", "110-987-654321");

        Long clientProfileId = clientProfileRepository.findByAccountIdAndDeletedAtIsNull(
                accountRepository.findByEmailAndRoleAndDeletedAtIsNull(CLIENT_EMAIL, Role.CLIENT)
                        .orElseThrow().getId()).orElseThrow().getId();
        projectId = seedProject(clientProfileId);
    }

    private Long saveTerms(TermsCode code, String title, boolean required, String targetRole) {
        return termsRepository.save(new TermsJpaEntity(
                null, code, code.getType(), "v1.0", title, "본문", required, targetRole,
                LocalDateTime.now().minusDays(1)
        )).getId();
    }

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

    private Cookie signUpAndLoginClient() throws Exception {
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

        return loginResult.getResponse().getCookie("accessToken");
    }

    private Cookie signUpAndLoginFreelancer(String email, String name, String phone, String bankAccountNo)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("phone", phone);
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("passwordConfirm", PASSWORD);
        body.put("birthDate", "1995-03-01");
        body.put("card", Map.of("cardNumber", "1234-5678-1234-5678", "cardBrand", "신한카드"));
        body.put("bankAccount", Map.of("bankCode", "088", "accountNo", bankAccountNo, "accountHolder", name));
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
                                .formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        return loginResult.getResponse().getCookie("accessToken");
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

    private Long createSiteReview(Cookie reviewerToken, Long contractId, Long revieweeAccountId, int siteScore)
            throws Exception {
        // 상대방은 서버가 계약에서 가져온다. 여기서는 프리랜서가 클라이언트를 평가하는 계약으로 세운다.
        ContractDetail contractDetail = ContractDetailStub.of(contractId, projectId, "페어링 웹 리뉴얼",
                revieweeAccountId, "주식회사 페어링", null, "이프리");
        given(contractQueryUseCase.getDetail(eq(contractId), any())).willReturn(contractDetail);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contractId", contractId);
        body.put("counterpart", Map.of("score", 5, "content", "협업이 좋았습니다."));
        body.put("site", Map.of("score", siteScore, "content", "사이트 이용 후기입니다."));

        mockMvc.perform(post("/api/v1/reviews")
                        .cookie(reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        // 방금 만든 건이 항상 가장 큰 id이므로 score 중복과 무관하게 정확히 집을 수 있다.
        return siteReviewRepository.findAll().stream()
                .max(java.util.Comparator.comparing(
                        com.pairing.review.infrastructure.persistence.SiteReviewJpaEntity::getId))
                .orElseThrow().getId();
    }

    private void promote(Cookie adminAccessToken, Long siteReviewId) throws Exception {
        mockMvc.perform(put("/api/v1/reviews/admin/site-reviews/" + siteReviewId + "/visibility")
                        .cookie(adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"visibility":"PUBLIC","promoted":true}"""))
                .andExpect(status().isOk());
    }

    private Long clientAccountId() {
        return accountRepository.findByEmailAndRoleAndDeletedAtIsNull(CLIENT_EMAIL, Role.CLIENT)
                .orElseThrow().getId();
    }

    @Test
    @DisplayName("로그인 없이도 공개+홍보활용+4점 이상인 리뷰만 마스킹된 이름으로 조회된다")
    void findSiteReviewsIsPublicAndFiltersByVisibilityPromotedAndScore() throws Exception {
        Long goodReviewId = createSiteReview(freelancerAccessToken, 901L, clientAccountId(), 5);
        Long lowScoreReviewId = createSiteReview(lowScoreFreelancerAccessToken, 902L, clientAccountId(), 3);

        Cookie adminAccessToken = loginAsAdmin();
        promote(adminAccessToken, goodReviewId);
        promote(adminAccessToken, lowScoreReviewId);

        // 쿠키 없이(비로그인) 호출
        mockMvc.perform(get("/api/v1/home/site-reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].score").value(5))
                .andExpect(jsonPath("$.data[0].writerRole").value("FREELANCER"))
                .andExpect(jsonPath("$.data[0].writerName").value("이**"));
    }

    @Test
    @DisplayName("관리자가 공개로 바꿔도 홍보 활용으로 설정하지 않으면 노출되지 않는다")
    void findSiteReviewsExcludesPublicButNotPromoted() throws Exception {
        Long siteReviewId = createSiteReview(freelancerAccessToken, 903L, clientAccountId(), 5);

        Cookie adminAccessToken = loginAsAdmin();
        mockMvc.perform(put("/api/v1/reviews/admin/site-reviews/" + siteReviewId + "/visibility")
                        .cookie(adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"visibility":"PUBLIC","promoted":false}"""))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/home/site-reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("size 파라미터만큼만 최신순으로 반환된다")
    void findSiteReviewsRespectsSizeParam() throws Exception {
        Long first = createSiteReview(freelancerAccessToken, 904L, clientAccountId(), 5);
        Long second = createSiteReview(lowScoreFreelancerAccessToken, 905L, clientAccountId(), 5);

        Cookie adminAccessToken = loginAsAdmin();
        promote(adminAccessToken, first);
        promote(adminAccessToken, second);

        mockMvc.perform(get("/api/v1/home/site-reviews").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].writerName").value("박**"));
    }
}
