package com.pairing.auth.presentation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.global.security.GlobalJwtProvider;
import com.pairing.account.infrastructure.persistence.SpringDataAccountRepository;
import com.pairing.account.infrastructure.persistence.SpringDataClientProfileRepository;
import com.pairing.account.infrastructure.persistence.SpringDataFreelancerProfileRepository;
import com.pairing.account.infrastructure.persistence.SpringDataPaymentMethodRepository;
import com.pairing.account.domain.model.PaymentMethodType;
import com.pairing.account.infrastructure.persistence.SpringDataSocialAccountRepository;
import com.pairing.global.port.out.DataEncryptionPort;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;
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
import java.util.Optional;

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
    @Autowired
    private DataEncryptionPort dataEncryptionPort;

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

    @Autowired
    private GlobalJwtProvider globalJwtProvider;
    @Value("${jwt.secret-key}")
    private String jwtSecretKey;

    @BeforeEach
    void setUp() {
        termsAgreementRepository.deleteAll();
        paymentMethodRepository.deleteAll();
        clientProfileRepository.deleteAll();
        freelancerProfileRepository.deleteAll();
        socialAccountRepository.deleteAll();
        accountRepository.deleteAll();
        termsRepository.deleteAll();

        // 서비스 이용약관은 같은 코드로 역할별 두 행을 둔다. 화면에는 하나만 노출된다.
        clientTermsId = saveTerms(TermsCode.SERVICE, "서비스 이용약관 동의", true, "CLIENT");
        freelancerTermsId = saveTerms(TermsCode.SERVICE, "서비스 이용약관 동의", true, "FREELANCER");
        privacyTermsId = saveTerms(TermsCode.PRIVACY_CONSENT, "개인정보 수집 및 이용 동의", true, null);
        marketingTermsId = saveTerms(TermsCode.MARKETING, "마케팅 정보 수신 동의", false, null);

        // 이메일 인증과 세션 판정은 Redis 담당이므로 통과하도록 둔다.
        given(verifiedMarkerPort.isVerified(anyString(), any())).willReturn(true);
        given(sessionRegistryPort.isAlive(any(), anyString())).willReturn(true);
    }

    private Long saveTerms(TermsCode code, String title, boolean required, String targetRole) {
        return termsRepository.save(new TermsJpaEntity(
                null, code, code.getType(), "v1.0", title, "본문", required, targetRole,
                LocalDateTime.now().minusDays(1)
        )).getId();
    }

    private Map<String, Object> card() {
        return Map.of("cardNumber", "1234-5678-1234-5678", "cardBrand", "SHINHAN");
    }

    private Map<String, Object> address() {
        // 위젯이 주는 모양 그대로다. roadAddress 에 시·도·시·군·구가 이미 들어 있다.
        return Map.of("sido", "서울", "sigungu", "강남구", "roadAddress", "서울 강남구 테헤란로 1",
                "addressDetail", "10층", "zipCode", "06234");
    }

    private Map<String, Object> bankAccount() {
        return Map.of("bankCode", "088", "accountNo", "110-123-456789", "accountHolder", "홍길동");
    }

    private Map<String, Object> clientSignUpBody() {
        // Map.of 는 10쌍까지만 받아서 항목이 늘면 컴파일이 깨진다.
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("companyName", "주식회사 페어링");
        body.put("businessNo", "1234567890");
        body.put("businessField", "IT_CONTENTS_AI");
        body.put("employeeCount", "SIZE_10_49");
        body.put("address", address());
        body.put("email", EMAIL);
        body.put("name", "홍길동");
        body.put("phone", "010-1234-5678");
        body.put("password", PASSWORD);
        body.put("passwordConfirm", PASSWORD);
        body.put("card", card());
        body.put("bankAccount", bankAccount());
        return body;
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
    @DisplayName("만료가 가까운 액세스 토큰은 요청 처리 중에 갱신되고, 여유가 있으면 그대로 둔다")
    void slidingSessionRenewsAccessTokenNearExpiry() throws Exception {
        signUpClient();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"owner@pairing.com","password":"Passw0rd!","role":"CLIENT"}"""))
                .andExpect(status().isOk())
                .andReturn();
        Cookie issued = loginResult.getResponse().getCookie("accessToken");

        // 방금 발급받아 수명이 넉넉하면(1시간 중 1시간 남음) 새 쿠키를 내려보내지 않는다.
        // 매 요청마다 발급하면 응답마다 Set-Cookie 가 붙고 병렬 요청이 서로를 덮어쓴다.
        MvcResult fresh = mockMvc.perform(get("/api/v1/auth/me").cookie(issued))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(fresh.getResponse().getCookie("accessToken")).isNull();

        // 남은 수명이 임계값(30분) 아래로 내려가면 만료를 미룬 토큰을 새로 내려준다.
        Cookie nearExpiry = new Cookie("accessToken", shortLivedCopyOf(issued.getValue()));
        MvcResult renewed = mockMvc.perform(get("/api/v1/auth/me").cookie(nearExpiry))
                .andExpect(status().isOk())
                .andReturn();

        Cookie renewedCookie = renewed.getResponse().getCookie("accessToken");
        assertThat(renewedCookie).isNotNull();
        assertThat(renewedCookie.getValue()).isNotEqualTo(nearExpiry.getValue());
        assertThat(globalJwtProvider.getExpiration(renewedCookie.getValue()))
                .isAfter(globalJwtProvider.getExpiration(nearExpiry.getValue()));
        // 세션 ID 는 그대로 물려받아야 한다. 새로 만들면 진행 중이던 다른 요청이
        // "다른 기기 로그인"으로 오인되어 끊긴다.
        assertThat(globalJwtProvider.getSessionId(renewedCookie.getValue()))
                .isEqualTo(globalJwtProvider.getSessionId(nearExpiry.getValue()));
    }

    @Test
    @DisplayName("Authorization 헤더로 인증하면 쿠키를 심지 않는다")
    void slidingSessionDoesNotTouchCookiesForBearerToken() throws Exception {
        signUpClient();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"owner@pairing.com","password":"Passw0rd!","role":"CLIENT"}"""))
                .andExpect(status().isOk())
                .andReturn();

        String nearExpiry = shortLivedCopyOf(loginResult.getResponse().getCookie("accessToken").getValue());

        // 쿠키를 쓰지 않는 호출(Swagger·스크립트)에 쿠키를 심으면 같은 브라우저의 다른 로그인이 덮어써진다.
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + nearExpiry))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getCookie("accessToken")).isNull();
    }

    /** 같은 subject·role·sid 를 담되 수명만 10분인 액세스 토큰. 만료 임박 상황을 만든다. */
    private String shortLivedCopyOf(String accessToken) {
        GlobalJwtProvider shortLived = new GlobalJwtProvider();
        ReflectionTestUtils.setField(shortLived, "secretKey", jwtSecretKey);
        ReflectionTestUtils.setField(shortLived, "accessTokenExpiration", 600_000L);
        ReflectionTestUtils.setField(shortLived, "refreshTokenExpiration", 600_000L);
        ReflectionTestUtils.setField(shortLived, "cookieDomain", "");
        ReflectionTestUtils.setField(shortLived, "cookieSecure", false);
        ReflectionTestUtils.invokeMethod(shortLived, "init");

        var claims = globalJwtProvider.parseClaims(accessToken);
        return shortLived.createAccessToken(claims.getSubject(), claims.get("role", String.class),
                claims.get(GlobalJwtProvider.SESSION_ID_CLAIM, String.class));
    }

    @Test
    @DisplayName("가입 동의 항목은 세 개이며 게시용 문서는 빠진다")
    void findTermsByRole() throws Exception {
        saveTerms(TermsCode.PRIVACY_POLICY, "개인정보 처리방침", false, null);

        mockMvc.perform(get("/api/v1/terms").param("role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[?(@.code == 'PRIVACY_POLICY')]").isEmpty())
                .andExpect(jsonPath("$.data[?(@.type == 'POLICY')]").isEmpty())
                .andExpect(jsonPath("$.data[?(@.code == 'MARKETING')].required").value(false));
    }

    @Test
    @DisplayName("문서 조회는 개인정보 처리방침까지 함께 내려준다")
    void findTermsDocuments() throws Exception {
        saveTerms(TermsCode.PRIVACY_POLICY, "개인정보 처리방침", false, null);

        mockMvc.perform(get("/api/v1/terms/documents").param("role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[?(@.code == 'PRIVACY_POLICY')]").isNotEmpty());
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

        // 클라이언트는 메인·프로필에 기업명이 나가야 해서 담당자명과 기업명을 따로 내린다.
        // 하나로 합치면 프로필의 "담당자" 줄까지 기업명이 된다.
        mockMvc.perform(get("/api/v1/auth/me").cookie(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.role").value("CLIENT"))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.companyName").value("주식회사 페어링"));
    }

    @Test
    @DisplayName("프리랜서는 기업명 없이 담당자명만 내려온다")
    void freelancerMeHasNoCompanyName() throws Exception {
        Map<String, Object> freelancer = new java.util.HashMap<>(Map.of(
                "name", "김프리",
                "phone", "010-2222-3333",
                "email", EMAIL,
                "password", PASSWORD,
                "passwordConfirm", PASSWORD,
                "birthDate", "1995-03-01",
                "card", card(),
                "bankAccount", bankAccount()));
        freelancer.put("address", address());
        freelancer.put("agreements", List.of(
                Map.of("termsId", freelancerTermsId, "agreed", true),
                Map.of("termsId", privacyTermsId, "agreed", true),
                Map.of("termsId", marketingTermsId, "agreed", true)));

        mockMvc.perform(post("/api/v1/auth/signup/freelancer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(freelancer)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"owner@pairing.com","password":"Passw0rd!","role":"FREELANCER"}"""))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(get("/api/v1/auth/me").cookie(loginResult.getResponse().getCookie("accessToken")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("김프리"))
                .andExpect(jsonPath("$.data.companyName").isEmpty());
    }

    @Test
    @DisplayName("가입하면 카드와 계좌가 암호화되어 함께 저장된다")
    void signUpStoresPaymentMethods() throws Exception {
        signUpClient();

        Long accountId = accountRepository.findAll().get(0).getId();
        var methods = paymentMethodRepository.findAllByAccountIdAndDeletedAtIsNull(accountId);
        assertThat(methods).hasSize(2);

        var card = methods.stream()
                .filter(m -> m.getMethodType() == PaymentMethodType.CARD).findFirst().orElseThrow();
        var bank = methods.stream()
                .filter(m -> m.getMethodType() == PaymentMethodType.BANK_ACCOUNT).findFirst().orElseThrow();

        // 하이픈은 제거되고, 평문은 저장되지 않으며, 카드 끝 4자리만 따로 남는다.
        assertThat(dataEncryptionPort.decrypt(card.getCardNumberEnc())).isEqualTo("1234567812345678");
        assertThat(card.getCardLast4()).isEqualTo("5678");
        assertThat(card.getCardBrand()).isEqualTo("SHINHAN");

        assertThat(bank.getBankCode()).isEqualTo("088");
        assertThat(dataEncryptionPort.decrypt(bank.getAccountNoEnc())).isEqualTo("110123456789");
        assertThat(bank.getAccountHolder()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("등록되지 않은 은행 코드는 AC_006으로 막고 계정도 남기지 않는다")
    void signUpWithUnknownBankCode() throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(clientSignUpBody());
        body.put("bankAccount", Map.of(
                "bankCode", "999", "accountNo", "110123456789", "accountHolder", "홍길동"));
        body.put("agreements", clientAgreements());

        mockMvc.perform(post("/api/v1/auth/signup/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AC_006"));

        assertThat(accountRepository.count()).isZero();
    }

    @Test
    @DisplayName("휴대폰번호·사업자등록번호·은행코드가 빠지면 500이 아니라 400으로 막는다")
    void signUpRejectsMissingRequiredFieldsWithFieldError() throws Exception {
        // @Pattern 은 null 을 통과시킨다(Bean Validation 명세). @NotBlank 가 없으면 도메인이나
        // DB(NOT NULL)까지 내려가서, 휴대폰번호의 경우 500 이 나갔다.
        for (String missing : List.of("phone", "businessNo")) {
            Map<String, Object> body = new java.util.HashMap<>(clientSignUpBody());
            body.remove(missing);
            body.put("agreements", clientAgreements());

            mockMvc.perform(post("/api/v1/auth/signup/client")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_002"));
        }

        Map<String, Object> noBankCode = new java.util.HashMap<>(clientSignUpBody());
        noBankCode.put("bankAccount", Map.of("accountNo", "110-123-456789", "accountHolder", "홍길동"));
        noBankCode.put("agreements", clientAgreements());

        mockMvc.perform(post("/api/v1/auth/signup/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noBankCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_002"));

        assertThat(accountRepository.count()).isZero();
    }

    @Test
    @DisplayName("기업 주소가 빠지면 400으로 막는다")
    void signUpWithoutAddress() throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(clientSignUpBody());
        body.remove("address");
        body.put("agreements", clientAgreements());

        mockMvc.perform(post("/api/v1/auth/signup/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_002"));
    }

    @Test
    @DisplayName("프리랜서도 주소 없이는 가입할 수 없고, 시·도와 도로명이 각각 필수다")
    void freelancerSignUpRequiresAddress() throws Exception {
        // 주소 자체가 없는 경우
        mockMvc.perform(post("/api/v1/auth/signup/freelancer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(freelancerSignUpBody(null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_002"));

        // 시·도만 빠진 경우
        mockMvc.perform(post("/api/v1/auth/signup/freelancer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(freelancerSignUpBody(
                                Map.of("sigungu", "강남구", "roadAddress", "테헤란로 1")))))
                .andExpect(status().isBadRequest());

        // 도로명만 빠진 경우
        mockMvc.perform(post("/api/v1/auth/signup/freelancer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(freelancerSignUpBody(
                                Map.of("sido", "서울", "sigungu", "강남구")))))
                .andExpect(status().isBadRequest());

        assertThat(accountRepository.count()).isZero();
    }

    @Test
    @DisplayName("시·군·구가 없는 세종시도 가입되고, 주소는 빈 칸 없이 한 줄로 합쳐진다")
    void freelancerSignUpAllowsMissingSigungu() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/freelancer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(freelancerSignUpBody(Map.of(
                                "sido", "세종특별자치시",
                                "sigungu", "",
                                "roadAddress", "세종특별자치시 한누리대로 2130",
                                "addressDetail", "3층",
                                "zipCode", "30151")))))
                .andExpect(status().isCreated());

        var profile = freelancerProfileRepository.findAll().get(0);
        assertThat(profile.getAddress()).isEqualTo("세종특별자치시 한누리대로 2130 3층");
        assertThat(profile.getAddressParts().getSigungu()).isNull();
        assertThat(profile.getAddressParts().getZipCode()).isEqualTo("30151");
    }

    /** {@code address} 가 null 이면 주소 항목 자체를 뺀 바디를 만든다. */
    private Map<String, Object> freelancerSignUpBody(Map<String, Object> address) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("name", "김프리");
        body.put("phone", "010-2222-3333");
        body.put("email", EMAIL);
        body.put("password", PASSWORD);
        body.put("passwordConfirm", PASSWORD);
        body.put("birthDate", "1995-03-01");
        if (address != null) {
            body.put("address", address);
        }
        body.put("card", card());
        body.put("bankAccount", bankAccount());
        body.put("agreements", List.of(
                Map.of("termsId", freelancerTermsId, "agreed", true),
                Map.of("termsId", privacyTermsId, "agreed", true)));
        return body;
    }

    @Test
    @DisplayName("카드 정보가 빠지면 400으로 막는다")
    void signUpWithoutCard() throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(clientSignUpBody());
        body.remove("card");
        body.put("agreements", clientAgreements());

        mockMvc.perform(post("/api/v1/auth/signup/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_002"));
    }

    @Test
    @DisplayName("은행 목록을 코드와 이름으로 내려준다")
    void findBanks() throws Exception {
        mockMvc.perform(get("/api/v1/meta/banks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == '088')].label").value("신한은행"));
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
                "birthDate", "1995-03-01",
                "card", card(),
                "bankAccount", bankAccount()));
        freelancer.put("address", address());
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

    // ==========================================
    // 세션 종료 시 쿠키 만료
    //
    // 쿠키는 HttpOnly 라 프론트가 지울 수 없다. 서버가 만료시켜 주지 않으면 죽은 토큰이
    // 브라우저에 남아 로그인 화면에서까지 같은 401이 반복되고, "다른 기기에서 로그인" 모달을
    // 무한히 다시 띄운다. 아래 테스트들이 그 탈출구를 고정한다.
    // ==========================================

    @Test
    @DisplayName("다른 기기가 로그인하면 GLOBAL_011과 함께 인증 쿠키가 만료된다")
    void terminatedSessionExpiresAuthCookies() throws Exception {
        signUpClient();
        Cookie accessToken = login().getResponse().getCookie("accessToken");

        // 다른 기기가 로그인해 SESSION:{accountId} 가 새 sid 로 덮어써진 상황.
        given(sessionRegistryPort.isAlive(any(), anyString())).willReturn(false);

        MvcResult result = mockMvc.perform(get("/api/v1/auth/me").cookie(accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_011"))
                .andReturn();

        assertCookiesExpired(result);
    }

    @Test
    @DisplayName("재발급도 다른 기기 로그인이면 AU_015와 함께 쿠키를 만료시킨다")
    void reissueAfterOtherDeviceLoginExpiresAuthCookies() throws Exception {
        signUpClient();
        Cookie refreshToken = login().getResponse().getCookie("refreshToken");

        // 저장값이 다른 토큰으로 덮어써졌고, sid 도 이미 교체됐다 = 다른 기기가 세션을 가져갔다.
        given(tokenStorePort.find(any())).willReturn(Optional.of("another-device-refresh-token"));
        given(sessionRegistryPort.isAlive(any(), anyString())).willReturn(false);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AU_015"))
                .andReturn();

        assertCookiesExpired(result);
    }

    @Test
    @DisplayName("같은 세션의 중복 재발급은 AU_016으로 거절하고 쿠키는 건드리지 않는다")
    void concurrentReissueInSameSessionKeepsAuthCookies() throws Exception {
        signUpClient();
        Cookie refreshToken = login().getResponse().getCookie("refreshToken");

        // 탭 두 개가 동시에 재발급을 요청해 저장값은 이미 갱신됐지만, sid 는 그대로다.
        // 세션이 끊긴 게 아니므로 AU_015 로 응답하거나 쿠키를 지우면 멀쩡한 로그인이 날아간다.
        given(tokenStorePort.find(any())).willReturn(Optional.of("token-from-the-winning-tab"));
        given(sessionRegistryPort.isAlive(any(), anyString())).willReturn(true);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AU_016"))
                .andReturn();

        assertThat(result.getResponse().getCookies()).isEmpty();
    }

    private MvcResult login() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"owner@pairing.com","password":"Passw0rd!","role":"CLIENT"}"""))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** 두 쿠키 모두 값이 비고 수명이 0이어야 브라우저가 즉시 삭제한다. */
    private void assertCookiesExpired(MvcResult result) {
        for (String name : List.of("accessToken", "refreshToken")) {
            Cookie cookie = result.getResponse().getCookie(name);
            assertThat(cookie).as("%s 만료 쿠키", name).isNotNull();
            assertThat(cookie.getValue()).isEmpty();
            assertThat(cookie.getMaxAge()).isZero();
            // 삭제 쿠키도 생성 때와 같은 Path 여야 브라우저가 같은 쿠키로 인식한다.
            assertThat(cookie.getPath()).isEqualTo("/");
        }
    }
}
