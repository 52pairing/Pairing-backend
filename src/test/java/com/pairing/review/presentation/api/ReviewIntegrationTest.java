package com.pairing.review.presentation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.account.domain.model.AccountStatus;
import com.pairing.contract.application.result.ContractDetail;
import com.pairing.contract.application.result.ContractSummary;
import com.pairing.contract.application.usecase.ContractQueryUseCase;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.contract.exception.ContractErrorCode;
import com.pairing.global.config.ContractDetailStub;
import com.pairing.global.exception.BusinessException;
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
import com.pairing.global.config.SettlementResultStub;
import com.pairing.settlement.application.usecase.SettlementQueryUseCase;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.model.SettlementStatus;
import com.pairing.review.infrastructure.persistence.SiteReviewJpaEntity;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 리뷰 작성 -> 받은/작성한 목록 -> 요약, 관리자 사이트 리뷰 흐름을 실제 요청으로 확인한다.
 *
 * <p>프로젝트와 상대방은 서버가 계약에서 유도한다. 계약을 진짜로 만들려면 매칭→협상→타결까지
 * 태워야 해서, 계약 조회만 스텁으로 대신하고 나머지는 실제로 저장·조회한다.
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

    // 계약 조회만 대신한다. 리뷰 저장·조회는 실제 로직 그대로 검증한다.
    @MockitoBean
    private ContractQueryUseCase contractQueryUseCase;

    // 성공보수 납부 여부. 정산 도메인을 통째로 세우지 않고 결과만 대신한다.
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

        signUpAndLoginClient();
        signUpAndLoginFreelancer();

        Long clientProfileId = clientProfileRepository.findByAccountIdAndDeletedAtIsNull(clientAccountId)
                .orElseThrow().getId();
        projectId = seedProject(clientProfileId);

        // 스텁을 given(...) 안에서 만들면 mock 안에서 mock 을 세우게 되어 Mockito 가 막는다. 먼저 만든다.
        ContractDetail contractDetail = ContractDetailStub.of(CONTRACT_ID, projectId, "페어링 웹 리뉴얼",
                clientAccountId, "주식회사 페어링", freelancerAccountId, "이프리");
        given(contractQueryUseCase.getDetail(eq(CONTRACT_ID), any())).willReturn(contractDetail);
        // 스텁하지 않으면 mock 이 null 을 돌려줘 작성 대기 조회가 NPE 로 죽는다.
        given(contractQueryUseCase.findMine(any(), any(), any(), any())).willReturn(Page.empty());

        // 리뷰는 본인 성공보수 납부까지 확인한다. 기본값은 "냈다"로 두고, 안 낸 상황만 개별 테스트에서 뒤집는다.
        givenSuccessFeePaid(true);
    }

    /** 본인 성공보수 결제 여부 스텁. 결제했으면 결과가 1건, 아니면 0건이다. */
    private void givenSuccessFeePaid(boolean paid) {
        given(settlementQueryUseCase.findMine(any(), any(), eq(SettlementPhase.SUCCESS_FEE),
                eq(SettlementStatus.PAID), any()))
                .willReturn(paid ? new PageImpl<>(List.of(SettlementResultStub.paidSuccessFee()))
                        : Page.empty());
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

    /** 상대방은 서버가 계약에서 유도하므로 요청에 넣지 않는다. */
    private Map<String, Object> reviewCreateBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contractId", CONTRACT_ID);
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
                        .content(objectMapper.writeValueAsString(reviewCreateBody())))
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
    @DisplayName("없는 계약으로 리뷰를 쓰면 저장 전에 막힌다")
    void createReviewWithUnknownContractIsRejected() throws Exception {
        // 계약을 확인하지 않으면 없는 참조로 저장하다 500(GLOBAL_001)이 나간다.
        given(contractQueryUseCase.getDetail(eq(888L), any()))
                .willThrow(new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));

        Map<String, Object> body = reviewCreateBody();
        body.put("contractId", 888L);

        mockMvc.perform(post("/api/v1/reviews")
                        .cookie(freelancerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CT_001"));
    }

    @Test
    @DisplayName("대금 지급 전이면 리뷰를 쓸 수 없다")
    void createReviewBeforeSettlementIsRejected() throws Exception {
        // 성공보수 결제 전에는 프로젝트가 완료 대기다. 이 상태에서는 리뷰가 열리지 않는다. (P51)
        jdbcTemplate.update("UPDATE project SET status = ?, payment_status = ? WHERE id = ?",
                "COMPLETION_PENDING", "DEPOSIT_PAID", PROJECT_ID);

        mockMvc.perform(post("/api/v1/reviews")
                        .cookie(freelancerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewCreateBody())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RV_004"));
    }

    @Test
    @DisplayName("프로젝트가 CLOSED 여도 본인 성공보수를 안 냈으면 리뷰를 못 쓴다")
    void reviewBlockedWhenOwnSuccessFeeUnpaid() throws Exception {
        // 프로젝트 CLOSED 는 클라이언트가 성공보수를 냈다는 뜻일 뿐이다(P30).
        // 프리랜서 본인이 안 냈으면 "대금 지급 완료 후 작성"(P51)을 만족하지 않는다.
        givenSuccessFeePaid(false);

        mockMvc.perform(post("/api/v1/reviews")
                        .cookie(freelancerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewCreateBody())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RV_004"));

        // 작성 대기 목록에서도 빠져야 한다. 목록에만 안 보이고 API 는 뚫리면 의미가 없다.
        mockMvc.perform(get("/api/v1/reviews/pending").cookie(freelancerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("리뷰 대상과 프로젝트는 요청이 아니라 계약에서 가져온다")
    void createReviewDerivesRevieweeAndProjectFromContract() throws Exception {
        // 프리랜서가 쓰면 상대는 클라이언트다. 요청에 상대 정보가 없어도 계약으로 정해진다.
        mockMvc.perform(post("/api/v1/reviews")
                        .cookie(freelancerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewCreateBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.projectTitle").value("페어링 웹 리뉴얼"));

        // 클라이언트가 받은 리뷰로 잡혀야 한다.
        mockMvc.perform(get("/api/v1/reviews/received").cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    @DisplayName("같은 계약을 같은 사람이 두 번 리뷰하면 막힌다")
    void duplicateReviewIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/reviews")
                        .cookie(freelancerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewCreateBody())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/reviews")
                        .cookie(freelancerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewCreateBody())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RV_001"));
    }

    @Test
    @DisplayName("대금 지급이 끝난 계약이 없으면 작성 대기 목록은 비어 있다")
    void pendingReviewsIsEmptyWithoutCompletedContract() throws Exception {
        mockMvc.perform(get("/api/v1/reviews/pending").cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("성공보수까지 결제되면(프로젝트 CLOSED) 작성 대기로 뜨고, 리뷰를 쓰면 목록에서 빠진다")
    void pendingReviewDisappearsAfterWriting() throws Exception {
        // 서비스는 계약 상태가 아니라 프로젝트가 CLOSED 인지로 거른다. 심어둔 프로젝트가 CLOSED 상태다.
        Contract completed = mock(Contract.class);
        given(completed.getId()).willReturn(CONTRACT_ID);
        given(completed.getProjectId()).willReturn(PROJECT_ID);
        given(completed.getStatus()).willReturn(ContractStatus.COMPLETED);
        // 서명·정산 플래그와 카드 표시용 값은 작성 대기 판정과 무관해서 비워 둔다.
        ContractSummary summary = new ContractSummary(completed, "페어링 웹 리뉴얼", null, "이프리",
                null, false, true, true, true, null);
        given(contractQueryUseCase.findMine(eq(clientAccountId), isNull(), isNull(), any()))
                .willReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/v1/reviews/pending").cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].contractId").value(CONTRACT_ID))
                .andExpect(jsonPath("$.data[0].projectTitle").value("페어링 웹 리뉴얼"))
                .andExpect(jsonPath("$.data[0].counterpartName").value("이프리"));

        mockMvc.perform(post("/api/v1/reviews")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewCreateBody())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/reviews/pending").cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("사이트 리뷰는 작성 시 홍보 제외 상태로 저장된다")
    void siteReviewIsNotPromotedOnCreate() throws Exception {
        // 홍보 설정 API 는 관리자 서버(pairing-admin)로 옮겼다. 여기서는 저장된 기본값만 본다.
        // 홍보가 꺼져 있어야 관리자가 고르기 전까지 메인에 뜨지 않는다.
        mockMvc.perform(post("/api/v1/reviews")
                        .cookie(freelancerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewCreateBody())))
                .andExpect(status().isCreated());

        SiteReviewJpaEntity saved = siteReviewRepository.findAll().get(0);
        assertThat(saved.isPromoted()).isFalse();
    }
}
