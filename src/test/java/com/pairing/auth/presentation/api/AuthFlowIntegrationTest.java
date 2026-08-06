package com.pairing.auth.presentation.api;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 가입 -> 로그인 -> 내 정보 조회까지 실제 요청으로 확인한다.
 *
 * <p>Redis에 의존하는 포트는 대체한다. 테스트 목적은 도메인 규칙과 API 계약이지 Redis 동작이 아니다.
 * DB는 H2를 쓰지만 매핑(enum, CHAR, BYTEA)이 실제로 저장되는지까지 함께 확인된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    private static final String EMAIL = "owner@pairing.com";
    private static final String PASSWORD = "Passw0rd!";

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

    // Redis / SMTP 의존 포트
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

    @BeforeEach
    void setUp() {
        termsAgreementRepository.deleteAll();
        paymentMethodRepository.deleteAll();
        clientProfileRepository.deleteAll();
        freelancerProfileRepository.deleteAll();
        socialAccountRepository.deleteAll();
        accountRepository.deleteAll();
        termsRepository.deleteAll();

        clientTermsId = saveTerms(TermsCode.SERVICE_CLIENT, "클라이언트 이용약관", true, "CLIENT");
        freelancerTermsId = saveTerms(TermsCode.SERVICE_FREELANCER, "프리랜서 이용약관", true, "FREELANCER");
        privacyTermsId = saveTerms(TermsCode.PRIVACY, "개인정보 수집·이용 동의", true, null);
        marketingTermsId = saveTerms(TermsCode.MARKETING, "마케팅 수신 동의", false, null);

        // 이메일 인증과 세션 판정은 Redis 담당이므로 통과하도록 둔다.
        given(verifiedMarkerPort.isVerified(anyString(), any())).willReturn(true);
        given(sessionRegistryPort.isAlive(any(), anyString())).willReturn(true);
    }

    private Long saveTerms(TermsCode code, String title, boolean required, String targetRole) {
        return termsRepository.save(new TermsJpaEntity(
                null, code, "v1.0", title, "본문", required, targetRole, LocalDateTime.now().minusDays(1)
        )).getId();
    }

    private Map<String, Object> clientSignUpBody() {
        return Map.of(
                "companyName", "주식회사 페어링",
                "businessNo", "1234567890",
                "businessField", "IT_CONTENTS_AI",
                "employeeCount", "SIZE_10_49",
                "email", EMAIL,
                "name", "홍길동",
                "phone", "010-1234-5678",
                "password", PASSWORD,
                "passwordConfirm", PASSWORD);
    }

    private String clientSignUpJson(List<Map<String, Object>> agreements) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(clientSignUpBody());
        body.put("agreements", agreements);
        return objectMapper.writeValueAsString(body);
    }

    private List<Map<String, Object>> clientAgreements() {
        return List.of(
                Map.of("termsId", clientTermsId, "agreed", true),
                Map.of("termsId", privacyTermsId, "agreed", true),
                Map.of("termsId", marketingTermsId, "agreed", false));
    }

    private void signUpClient() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientSignUpJson(clientAgreements())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("약관 조회는 역할별 최신 버전만 내려준다")
    void findTermsByRole() throws Exception {
        mockMvc.perform(get("/api/v1/terms").param("role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[?(@.code == 'SERVICE_FREELANCER')]").isEmpty());
    }

    @Test
    @DisplayName("클라이언트 가입 후 로그인하면 쿠키가 발급되고 내 정보를 조회할 수 있다")
    void signUpThenLoginThenMe() throws Exception {
        signUpClient();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"owner@pairing.com","password":"Passw0rd!","role":"CLIENT"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("CLIENT"))
                .andExpect(jsonPath("$.data.tempPassword").value(false))
                .andReturn();

        Cookie accessToken = loginResult.getResponse().getCookie("accessToken");
        assertThat(accessToken).isNotNull();
        assertThat(accessToken.isHttpOnly()).isTrue();
        assertThat(loginResult.getResponse().getCookie("refreshToken")).isNotNull();
        // 토큰은 본문에 실리지 않는다.
        assertThat(loginResult.getResponse().getContentAsString()).doesNotContain(accessToken.getValue());

        mockMvc.perform(get("/api/v1/auth/me").cookie(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.role").value("CLIENT"));
    }

    @Test
    @DisplayName("같은 이메일·휴대폰이라도 역할이 다르면 가입할 수 있다")
    void sameContactDifferentRoleIsAllowed() throws Exception {
        signUpClient();

        Map<String, Object> freelancer = new java.util.HashMap<>(Map.of(
                "name", "홍길동",
                "phone", "010-1234-5678",
                "email", EMAIL,
                "password", PASSWORD,
                "passwordConfirm", PASSWORD,
                "birthDate", "1995-03-01"));
        freelancer.put("agreements", List.of(
                Map.of("termsId", freelancerTermsId, "agreed", true),
                Map.of("termsId", privacyTermsId, "agreed", true),
                Map.of("termsId", marketingTermsId, "agreed", true)));

        mockMvc.perform(post("/api/v1/auth/signup/freelancer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(freelancer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("FREELANCER"));

        // 역할별로 계정이 따로 만들어졌는지 확인한다.
        assertThat(accountRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 역할로 같은 이메일을 다시 쓰면 AU_007로 막는다")
    void duplicatedEmailInSameRole() throws Exception {
        signUpClient();

        mockMvc.perform(post("/api/v1/auth/signup/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientSignUpJson(clientAgreements())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AU_007"));
    }

    @Test
    @DisplayName("이메일 인증을 마치지 않으면 AU_006으로 막는다")
    void signUpWithoutEmailVerification() throws Exception {
        given(verifiedMarkerPort.isVerified(anyString(), any())).willReturn(false);

        mockMvc.perform(post("/api/v1/auth/signup/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientSignUpJson(clientAgreements())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AU_006"));

        assertThat(accountRepository.count()).isZero();
    }

    @Test
    @DisplayName("필수 약관에 동의하지 않으면 TM_002로 막고 계정도 남지 않는다")
    void signUpWithoutRequiredTerms() throws Exception {
        List<Map<String, Object>> agreements = List.of(
                Map.of("termsId", clientTermsId, "agreed", true),
                Map.of("termsId", privacyTermsId, "agreed", false),
                Map.of("termsId", marketingTermsId, "agreed", false));

        mockMvc.perform(post("/api/v1/auth/signup/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientSignUpJson(agreements)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TM_002"));

        // 약관 기록 실패가 계정 생성까지 함께 롤백해야 한다.
        assertThat(accountRepository.count()).isZero();
        assertThat(clientProfileRepository.count()).isZero();
    }

    @Test
    @DisplayName("비밀번호 형식이 틀리면 AU_010으로 막는다")
    void signUpWithWeakPassword() throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(clientSignUpBody());
        body.put("password", "password");
        body.put("passwordConfirm", "password");
        body.put("agreements", clientAgreements());

        mockMvc.perform(post("/api/v1/auth/signup/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AU_010"));
    }

    @Test
    @DisplayName("전화번호 형식이 틀리면 필드명이 담긴 400으로 막는다")
    void signUpWithInvalidPhone() throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(clientSignUpBody());
        body.put("phone", "01012");
        body.put("agreements", clientAgreements());

        mockMvc.perform(post("/api/v1/auth/signup/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_002"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("phone")));
    }

    @Test
    @DisplayName("중복 확인은 역할별로 답한다")
    void duplicationCheckIsPerRole() throws Exception {
        signUpClient();

        mockMvc.perform(get("/api/v1/auth/exists/email").param("email", EMAIL).param("role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicated").value(true));

        mockMvc.perform(get("/api/v1/auth/exists/email").param("email", EMAIL).param("role", "FREELANCER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicated").value(false));
    }

    @Test
    @DisplayName("사업자등록번호 형식이 틀리면 500이 아니라 400으로 응답한다")
    void invalidBusinessNoParameter() throws Exception {
        mockMvc.perform(get("/api/v1/auth/exists/business-no").param("businessNo", "12-34"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_002"));
    }

    @Test
    @DisplayName("로그인하지 않으면 내 정보를 조회할 수 없다")
    void meRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_006"));
    }
}
