package com.pairing.account.presentation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.account.infrastructure.persistence.PaymentMethodJpaEntity;
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
import com.pairing.auth.domain.model.VerificationPurpose;
import com.pairing.global.port.out.DataEncryptionPort;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 마이페이지 &gt; 결제수단 카드·계좌 수정을 실제 요청으로 확인한다.
 *
 * <p>번호가 평문으로 남지 않는지, 하이픈을 넣어 보내도 가입 때와 같은 형태(숫자만)로 저장되는지를
 * H2 에 저장된 암호문을 직접 복호화해서 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentMethodIntegrationTest {

    private static final String EMAIL = "payment-method@pairing.com";
    private static final String PASSWORD = "Passw0rd!";
    private static final String SIGNUP_CARD_NUMBER = "1234-5678-1234-5678";
    private static final String SHINHAN_CODE = "088";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DataEncryptionPort dataEncryptionPort;

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

    private Long freelancerTermsId;
    private Long privacyTermsId;
    private Cookie accessToken;

    @BeforeEach
    void setUp() throws Exception {
        termsAgreementRepository.deleteAll();
        paymentMethodRepository.deleteAll();
        clientProfileRepository.deleteAll();
        freelancerProfileRepository.deleteAll();
        socialAccountRepository.deleteAll();
        accountRepository.deleteAll();
        termsRepository.deleteAll();

        freelancerTermsId = saveTerms(TermsCode.SERVICE, "서비스 이용약관 동의", true, "FREELANCER");
        privacyTermsId = saveTerms(TermsCode.PRIVACY_CONSENT, "개인정보 수집 및 이용 동의", true, null);

        given(verifiedMarkerPort.isVerified(anyString(), any())).willReturn(true);
        given(sessionRegistryPort.isAlive(any(), anyString())).willReturn(true);

        signUpAndLogin();
    }

    private Long saveTerms(TermsCode code, String title, boolean required, String targetRole) {
        return termsRepository.save(new TermsJpaEntity(
                null, code, code.getType(), "v1.0", title, "본문", required, targetRole,
                LocalDateTime.now().minusDays(1)
        )).getId();
    }

    private void signUpAndLogin() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "홍길동");
        body.put("phone", "010-3333-4444");
        body.put("email", EMAIL);
        body.put("password", PASSWORD);
        body.put("passwordConfirm", PASSWORD);
        body.put("birthDate", "1995-03-01");
        body.put("address", Map.of("sido", "서울", "sigungu", "강남구", "roadAddress", "서울 강남구 테헤란로 1", "addressDetail", "10층", "zipCode", "06234"));
        body.put("card", Map.of("cardNumber", SIGNUP_CARD_NUMBER, "cardBrand", "SHINHAN"));
        body.put("bankAccount", Map.of("bankCode", SHINHAN_CODE, "accountNo", "110-123-456789",
                "accountHolder", "홍길동"));
        body.put("agreements", List.of(
                Map.of("termsId", freelancerTermsId, "agreed", true),
                Map.of("termsId", privacyTermsId, "agreed", true)));

        mockMvc.perform(post("/api/v1/auth/signup/freelancer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","role":"FREELANCER"}"""
                                .formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        accessToken = loginResult.getResponse().getCookie("accessToken");
    }

    private PaymentMethodJpaEntity findStored(boolean card) {
        return paymentMethodRepository.findAll().stream()
                .filter(entity -> card == (entity.getMethodType().name().equals("CARD")))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("가입 시 카드번호는 하이픈을 제거한 숫자만 암호화되어 저장되고, 끝 4자리만 따로 남는다")
    void signupStoresNormalizedEncryptedCardNumber() {
        PaymentMethodJpaEntity stored = findStored(true);

        assertThat(dataEncryptionPort.decrypt(stored.getCardNumberEnc())).isEqualTo("1234567812345678");
        assertThat(stored.getCardLast4()).isEqualTo("5678");
    }

    @Test
    @DisplayName("카드를 수정하면 새 번호가 암호화 저장되고 끝 4자리도 함께 갱신된다")
    void updateCardReplacesEncryptedNumberAndLast4() throws Exception {
        String newCardNumber = "9999-8888-7777-6666";

        mockMvc.perform(put("/api/v1/accounts/me/payment-methods/card")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "cardBrand", "KB",
                                "cardNumber", newCardNumber,
                                "cardHolder", "홍길동"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.methodType").value("CARD"))
                // 저장은 enum 이름("KB"), 응답은 화면에 찍을 한글명이다.
                .andExpect(jsonPath("$.data.cardBrand").value("KB국민카드"))
                .andExpect(jsonPath("$.data.cardCompany").value("KB"))
                .andExpect(jsonPath("$.data.cardLast4").value("6666"))
                .andExpect(jsonPath("$.data.cardHolder").value("홍길동"))
                .andExpect(jsonPath("$.data.displayName").value("KB국민카드 **** 6666"))
                // 카드 응답에 계좌 필드가 섞이면 화면이 헷갈린다.
                .andExpect(jsonPath("$.data.bankName").doesNotExist())
                .andExpect(jsonPath("$.data.accountLast4").doesNotExist());

        PaymentMethodJpaEntity stored = findStored(true);
        assertThat(dataEncryptionPort.decrypt(stored.getCardNumberEnc())).isEqualTo("9999888877776666");
        assertThat(stored.getCardLast4()).isEqualTo("6666");
        assertThat(stored.getCardBrand()).isEqualTo("KB");
        assertThat(stored.getCardHolder()).isEqualTo("홍길동");

        // 조회 API 로도 바뀐 값이 나와야 한다.
        mockMvc.perform(get("/api/v1/accounts/me/payment-methods").cookie(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.methodType == 'CARD')].cardLast4").value("6666"))
                .andExpect(jsonPath("$.data[?(@.methodType == 'CARD')].cardHolder").value("홍길동"));
    }

    @Test
    @DisplayName("계좌를 수정하면 은행 코드와 예금주가 바뀌고 계좌번호는 암호화 저장된다")
    void updateBankAccountReplacesBankAndEncryptedNumber() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/me/payment-methods/bank-account")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "bankCode", "004",
                                "accountNo", "333-9999-11111",
                                "accountHolder", "김개발"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.methodType").value("BANK_ACCOUNT"))
                .andExpect(jsonPath("$.data.accountHolder").value("김개발"))
                // 은행 이름은 저장하지 않고 코드로 찾아서 내려준다.
                .andExpect(jsonPath("$.data.bankName").value("KB국민은행"))
                .andExpect(jsonPath("$.data.accountLast4").value("1111"))
                .andExpect(jsonPath("$.data.displayName").value("KB국민은행 **** 1111"));

        PaymentMethodJpaEntity stored = findStored(false);
        assertThat(stored.getBankCode()).isEqualTo("004");
        assertThat(stored.getAccountHolder()).isEqualTo("김개발");
        assertThat(stored.getAccountLast4()).isEqualTo("1111");
        assertThat(dataEncryptionPort.decrypt(stored.getAccountNoEnc())).isEqualTo("333999911111");
    }

    @Test
    @DisplayName("가입 시에도 계좌 끝 4자리가 저장되고, 조회에서 은행 이름으로 표시된다")
    void signupStoresAccountLast4AndShowsBankName() throws Exception {
        PaymentMethodJpaEntity stored = findStored(false);
        assertThat(stored.getAccountLast4()).isEqualTo("6789");

        mockMvc.perform(get("/api/v1/accounts/me/payment-methods").cookie(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.methodType == 'BANK_ACCOUNT')].bankName").value("신한은행"))
                .andExpect(jsonPath("$.data[?(@.methodType == 'BANK_ACCOUNT')].accountLast4").value("6789"))
                .andExpect(jsonPath("$.data[?(@.methodType == 'BANK_ACCOUNT')].displayName")
                        .value("신한은행 **** 6789"));
    }

    @Test
    @DisplayName("지원하지 않는 은행 코드면 AC_006 으로 막고 기존 계좌는 그대로 남는다")
    void updateBankAccountWithUnknownBankCodeFails() throws Exception {
        // 형식(숫자)은 맞지만 목록에 없는 코드다. 저장 직전 BankCode.find 에서 걸린다.
        mockMvc.perform(put("/api/v1/accounts/me/payment-methods/bank-account")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "bankCode", "999",
                                "accountNo", "333-9999-11111",
                                "accountHolder", "김개발"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AC_006"));

        PaymentMethodJpaEntity stored = findStored(false);
        assertThat(stored.getBankCode()).isEqualTo(SHINHAN_CODE);
        assertThat(stored.getAccountHolder()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("은행 코드에 카드사처럼 enum 이름을 보내면 형식 단계에서 막는다")
    void updateBankAccountRejectsEnumNameAsBankCode() throws Exception {
        // 은행만 숫자 코드고 카드사는 영문 코드라 헷갈리기 쉽다. 형식에서 걸러야
        // 어느 칸이 틀렸는지 프론트가 알 수 있다(도메인까지 내려가면 AC_006 한 덩어리로 나간다).
        mockMvc.perform(put("/api/v1/accounts/me/payment-methods/bank-account")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "bankCode", "SHINHAN",
                                "accountNo", "333-9999-11111",
                                "accountHolder", "김개발"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_002"));

        assertThat(findStored(false).getBankCode()).isEqualTo(SHINHAN_CODE);
    }

    @Test
    @DisplayName("카드 수정은 카드 행만, 계좌 수정은 계좌 행만 바꾼다")
    void updatesDoNotAffectTheOtherPaymentMethod() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/me/payment-methods/card")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "cardBrand", "KB",
                                "cardNumber", "9999-8888-7777-6666",
                                "cardHolder", "홍길동"))))
                .andExpect(status().isOk());

        PaymentMethodJpaEntity bankAccount = findStored(false);
        assertThat(bankAccount.getBankCode()).isEqualTo(SHINHAN_CODE);
        assertThat(dataEncryptionPort.decrypt(bankAccount.getAccountNoEnc())).isEqualTo("110123456789");
        assertThat(bankAccount.getCardNumberEnc()).isNull();

        PaymentMethodJpaEntity card = findStored(true);
        assertThat(card.getBankCode()).isNull();
        assertThat(card.getAccountNoEnc()).isNull();

        // 결제수단은 여전히 카드 1건 + 계좌 1건이다(수정이 새 행을 만들지 않는다).
        assertThat(paymentMethodRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("카드사 목록은 비로그인으로도 조회되고 code 를 그대로 보내면 된다")
    void findCardCompanies() throws Exception {
        mockMvc.perform(get("/api/v1/meta/card-companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == 'SHINHAN')].label").value("신한카드"))
                .andExpect(jsonPath("$.data[?(@.code == 'KB')].label").value("KB국민카드"));
    }

    @Test
    @DisplayName("카드번호가 16자리가 아니면 400 으로 막는다")
    void updateCardRejectsWrongCardNumberLength() throws Exception {
        for (String invalid : List.of("1234-5678-1234-567", "1234-5678-1234-56789", "1234-5678-1234")) {
            mockMvc.perform(put("/api/v1/accounts/me/payment-methods/card")
                            .cookie(accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "cardBrand", "KB",
                                    "cardNumber", invalid,
                                    "cardHolder", "홍길동"))))
                    .andExpect(status().isBadRequest());
        }

        // 하나도 저장되지 않았어야 한다.
        assertThat(dataEncryptionPort.decrypt(findStored(true).getCardNumberEnc()))
                .isEqualTo("1234567812345678");
    }

    @Test
    @DisplayName("모르는 카드사를 보내면 400 으로 막는다")
    void updateCardRejectsUnknownCardCompany() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/me/payment-methods/card")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                // 한글 카드사명은 더 이상 받지 않는다. enum 이름만 받는다.
                                "cardBrand", "신한카드",
                                "cardNumber", "9999-8888-7777-6666",
                                "cardHolder", "홍길동"))))
                .andExpect(status().isBadRequest());

        assertThat(findStored(true).getCardBrand()).isEqualTo("SHINHAN");
    }

    @Test
    @DisplayName("계좌번호가 10~14자리를 벗어나면 400 으로 막는다")
    void updateBankAccountRejectsWrongAccountNoLength() throws Exception {
        // 9자리(짧음) / 15자리(김)
        for (String invalid : List.of("123-456-789", "123-4567-89012345")) {
            mockMvc.perform(put("/api/v1/accounts/me/payment-methods/bank-account")
                            .cookie(accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "bankCode", "004",
                                    "accountNo", invalid,
                                    "accountHolder", "김개발"))))
                    .andExpect(status().isBadRequest());
        }

        assertThat(findStored(false).getBankCode()).isEqualTo(SHINHAN_CODE);
    }

    @Test
    @DisplayName("경계값(계좌 10자리·14자리)은 통과한다")
    void updateBankAccountAcceptsBoundaryLengths() throws Exception {
        for (String valid : List.of("1234567890", "12345678901234")) {
            mockMvc.perform(put("/api/v1/accounts/me/payment-methods/bank-account")
                            .cookie(accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "bankCode", "004",
                                    "accountNo", valid,
                                    "accountHolder", "김개발"))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("이메일 인증을 안 마치면 결제수단 조회부터 AU_006 으로 막는다")
    void paymentMethodsRequireEmailVerification() throws Exception {
        given(verifiedMarkerPort.isVerified(anyString(), eq(VerificationPurpose.PAYMENT_METHOD)))
                .willReturn(false);

        // 마스킹해서 내려도 은행명·예금주·끝 4자리가 단서가 되므로 수정만이 아니라 조회부터 막는다.
        mockMvc.perform(get("/api/v1/accounts/me/payment-methods").cookie(accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AU_006"));

        mockMvc.perform(put("/api/v1/accounts/me/payment-methods/card")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "cardBrand", "KB",
                                "cardNumber", "9999-8888-7777-6666",
                                "cardHolder", "홍길동"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AU_006"));

        mockMvc.perform(put("/api/v1/accounts/me/payment-methods/bank-account")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "bankCode", "004",
                                "accountNo", "333-9999-11111",
                                "accountHolder", "김개발"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AU_006"));

        // 막혔으니 아무것도 바뀌지 않았어야 한다.
        assertThat(findStored(true).getCardBrand()).isEqualTo("SHINHAN");
        assertThat(findStored(false).getBankCode()).isEqualTo(SHINHAN_CODE);
    }

    @Test
    @DisplayName("프로필 수정용 인증으로는 결제수단이 열리지 않는다")
    void profileUpdateVerificationDoesNotOpenPaymentMethods() throws Exception {
        // 사용자가 프로필 화면에서 받은 코드로 결제수단까지 열리면 안 된다.
        given(verifiedMarkerPort.isVerified(anyString(), eq(VerificationPurpose.PROFILE_UPDATE)))
                .willReturn(true);
        given(verifiedMarkerPort.isVerified(anyString(), eq(VerificationPurpose.PAYMENT_METHOD)))
                .willReturn(false);

        mockMvc.perform(get("/api/v1/accounts/me/payment-methods").cookie(accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AU_006"));
    }

    @Test
    @DisplayName("조회해도 인증 마커를 소비하지 않아 이어지는 수정이 막히지 않는다")
    void viewingPaymentMethodsDoesNotConsumeVerification() throws Exception {
        // 탭에 들어가 목록을 보고, 카드를 고치고, 계좌까지 고치는 흐름이 인증 한 번으로 끝나야 한다.
        mockMvc.perform(get("/api/v1/accounts/me/payment-methods").cookie(accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/accounts/me/payment-methods/card")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "cardBrand", "KB",
                                "cardNumber", "9999-8888-7777-6666",
                                "cardHolder", "홍길동"))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/accounts/me/payment-methods/bank-account")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "bankCode", "004",
                                "accountNo", "333-9999-11111",
                                "accountHolder", "김개발"))))
                .andExpect(status().isOk());

        // 마커를 지우면 유효 시간(app.auth.verified-marker-ttl)이 아니라 첫 요청에서 인증이 끝나 버린다.
        verify(verifiedMarkerPort, never()).clear(anyString(), eq(VerificationPurpose.PAYMENT_METHOD));
    }
}
