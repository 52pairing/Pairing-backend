package com.pairing.grade.presentation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.contract.application.result.ContractDetail;
import com.pairing.contract.application.usecase.ContractQueryUseCase;
import com.pairing.global.config.SettlementResultStub;
import com.pairing.settlement.application.usecase.SettlementQueryUseCase;
import com.pairing.global.config.ContractDetailStub;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 등급 기준표(공개)와 내 등급 현황(로그인 후, 리뷰 반영)을 실제 요청으로 확인한다.
 *
 * <p>completedProjectCount 는 계약 조회를 스텁으로 비워둬서 항상 0이다. 이 값을 세는 규칙
 * (프로젝트가 CLOSED 인 계약만)은 같은 판정을 쓰는 리뷰 작성 대기 테스트가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GradeIntegrationTest {

    private static final String CLIENT_EMAIL = "grade-client@pairing.com";
    private static final String FREELANCER_EMAIL = "grade-freelancer@pairing.com";
    private static final String PASSWORD = "Passw0rd!";
    private static final Long PROJECT_ID = 5101L;

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

    // 완료 건수 조회와 리뷰 작성 시 계약 확인에 쓰인다. 계약 생성 플로우까지 태우지 않는다.
    @MockitoBean
    private ContractQueryUseCase contractQueryUseCase;

    // 리뷰 작성 조건 중 '본인 성공보수 납부' 확인용. 정산 도메인을 세우지 않고 결과만 대신한다.
    @MockitoBean
    private SettlementQueryUseCase settlementQueryUseCase;

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
        // 완료 계약이 없는 상태가 기본값이다. 스텁하지 않으면 mock 이 null 을 돌려줘 등급 조회가 NPE 로 죽는다.
        given(contractQueryUseCase.findMine(any(), any(), any(), any())).willReturn(Page.empty());
        // 리뷰 작성은 본인 성공보수 납부까지 본다. 등급 검증이 목적이라 "냈다"로 고정한다.
        given(settlementQueryUseCase.findMine(any(), any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(SettlementResultStub.paidSuccessFee())));

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
        body.put("address", Map.of("sido", "서울", "sigungu", "강남구", "roadAddress", "서울 강남구 테헤란로 1", "addressDetail", "10층", "zipCode", "06234"));
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
        body.put("address", Map.of("sido", "서울", "sigungu", "강남구", "roadAddress", "서울 강남구 테헤란로 1", "addressDetail", "10층", "zipCode", "06234"));
        body.put("card", Map.of("cardNumber", "1234-5678-1234-5678", "cardBrand", "SHINHAN"));
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

    @Test
    @DisplayName("등급 기준표는 로그인 없이 역할별로 조회된다")
    void findAllReturnsTiersByRole() throws Exception {
        mockMvc.perform(get("/api/v1/grades").param("role", "FREELANCER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].grade").value("JUNIOR"))
                .andExpect(jsonPath("$.data[1].grade").value("SENIOR"))
                .andExpect(jsonPath("$.data[2].grade").value("MASTER"));

        mockMvc.perform(get("/api/v1/grades").param("role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].grade").value("SILVER"))
                .andExpect(jsonPath("$.data[1].grade").value("GOLD"))
                .andExpect(jsonPath("$.data[2].grade").value("DIAMOND"));
    }

    @Test
    @DisplayName("클라이언트/프리랜서가 아닌 역할로 조회하면 막힌다")
    void findAllRejectsInvalidRole() throws Exception {
        mockMvc.perform(get("/api/v1/grades").param("role", "ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GR_001"));
    }

    @Test
    @DisplayName("리뷰가 없으면 내 등급의 평균 별점은 비어있고, 리뷰가 쌓이면 반영된다")
    void myGradeReflectsReviews() throws Exception {
        mockMvc.perform(get("/api/v1/grades/me").cookie(freelancerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grade").value("JUNIOR"))
                .andExpect(jsonPath("$.data.ratingAverage").doesNotExist())
                .andExpect(jsonPath("$.data.nextGrade").value("SENIOR"))
                .andExpect(jsonPath("$.data.nextGradeGuide", org.hamcrest.Matchers.containsString("아직 리뷰가 없어")));

        // 상대방·프로젝트는 서버가 계약에서 가져온다. 계약 조회만 스텁으로 대신한다.
        ContractDetail contractDetail = ContractDetailStub.of(999L, projectId, "페어링 웹 리뉴얼",
                clientAccountId, "주식회사 페어링", freelancerAccountId, "이프리");
        given(contractQueryUseCase.getDetail(eq(999L), any())).willReturn(contractDetail);

        Map<String, Object> reviewBody = new LinkedHashMap<>();
        reviewBody.put("contractId", 999L);
        reviewBody.put("counterpart", Map.of("score", 5, "content", "일정 준수가 좋았습니다."));
        reviewBody.put("site", Map.of("score", 4, "content", "협상 과정이 편했습니다."));

        mockMvc.perform(post("/api/v1/reviews")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewBody)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/grades/me").cookie(freelancerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grade").value("JUNIOR"))
                .andExpect(jsonPath("$.data.ratingAverage").value(5.0))
                .andExpect(jsonPath("$.data.nextGradeGuide", org.hamcrest.Matchers.containsString("충족했습니다")));
    }

    @Test
    @DisplayName("클라이언트의 내 등급은 기본 SILVER이고 최고 등급 전 단계라 다음 등급이 있다")
    void myGradeForClientDefaultsToSilver() throws Exception {
        mockMvc.perform(get("/api/v1/grades/me").cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grade").value("SILVER"))
                .andExpect(jsonPath("$.data.nextGrade").value("GOLD"));
    }
}
