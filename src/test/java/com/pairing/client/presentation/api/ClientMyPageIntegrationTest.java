package com.pairing.client.presentation.api;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 클라이언트 마이페이지(기본정보) 조회·수정을 실제 요청으로 확인한다.
 *
 * <p>스텁이 아니라 H2 DB에 저장된 값을 읽어 확인한다(전화번호는 Account 엔티티, 나머지는 ClientProfile).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClientMyPageIntegrationTest {

    private static final String EMAIL = "client-mypage@pairing.com";
    private static final String PASSWORD = "Passw0rd!";
    private static final String ACCOUNT_PHONE = "010-1234-5678";

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
    private Long privacyTermsId;
    private Long marketingTermsId;
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

        clientTermsId = saveTerms(TermsCode.SERVICE, "서비스 이용약관 동의", true, "CLIENT");
        privacyTermsId = saveTerms(TermsCode.PRIVACY_CONSENT, "개인정보 수집 및 이용 동의", true, null);
        marketingTermsId = saveTerms(TermsCode.MARKETING, "마케팅 정보 수신 동의", false, null);

        given(verifiedMarkerPort.isVerified(anyString(), any())).willReturn(true);
        given(sessionRegistryPort.isAlive(any(), anyString())).willReturn(true);

        signUpAndLoginClient();
    }

    private Long saveTerms(TermsCode code, String title, boolean required, String targetRole) {
        return termsRepository.save(new TermsJpaEntity(
                null, code, code.getType(), "v1.0", title, "본문", required, targetRole,
                LocalDateTime.now().minusDays(1)
        )).getId();
    }

    private void signUpAndLoginClient() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("companyName", "주식회사 오이랩");
        body.put("businessNo", "1234567890");
        body.put("businessField", "IT_CONTENTS_AI");
        body.put("employeeCount", "SIZE_10_49");
        body.put("address", Map.of("sido", "서울", "sigungu", "강남구", "roadAddress", "서울 강남구 테헤란로 1", "addressDetail", "10층", "zipCode", "06234"));
        body.put("email", EMAIL);
        body.put("name", "김민준");
        body.put("phone", ACCOUNT_PHONE);
        body.put("password", PASSWORD);
        body.put("passwordConfirm", PASSWORD);
        body.put("card", Map.of("cardNumber", "1234-5678-1234-5678", "cardBrand", "SHINHAN"));
        body.put("bankAccount", Map.of("bankCode", "088", "accountNo", "110-123-456789", "accountHolder", "김민준"));
        body.put("agreements", List.of(
                Map.of("termsId", clientTermsId, "agreed", true),
                Map.of("termsId", privacyTermsId, "agreed", true),
                Map.of("termsId", marketingTermsId, "agreed", true)));

        mockMvc.perform(post("/api/v1/auth/signup/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","role":"CLIENT"}"""
                                .formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        accessToken = loginResult.getResponse().getCookie("accessToken");
    }

    @Test
    @DisplayName("마이페이지 조회는 계정 정보와 기업 정보를 함께 반환한다")
    void findMeReturnsAccountAndProfileInfo() throws Exception {
        mockMvc.perform(get("/api/v1/clients/me").cookie(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("주식회사 오이랩"))
                .andExpect(jsonPath("$.data.businessNo").value("1234567890"))
                .andExpect(jsonPath("$.data.name").value("김민준"))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.phone").value(ACCOUNT_PHONE.replace("-", "")))
                .andExpect(jsonPath("$.data.address").value("서울 강남구 테헤란로 1 10층"))
                .andExpect(jsonPath("$.data.addressParts.sido").value("서울"))
                .andExpect(jsonPath("$.data.addressParts.roadAddress").value("서울 강남구 테헤란로 1"))
                .andExpect(jsonPath("$.data.grade").value("SILVER"));
    }

    @Test
    @DisplayName("마이페이지 수정은 전화번호(계정)와 기업정보(프로필)를 함께 반영하고, 다시 조회해도 그대로 나온다")
    void updateMeUpdatesAccountPhoneAndProfile() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("companyName", "주식회사 페어링랩스");
        body.put("employeeCount", "SIZE_50_299");
        body.put("phone", "010-9999-0000");
        body.put("address", Map.of("sido", "서울", "sigungu", "강남구", "roadAddress", "서울 강남구 테헤란로 1", "addressDetail", "10층", "zipCode", "06234"));

        mockMvc.perform(patch("/api/v1/clients/me")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("주식회사 페어링랩스"))
                .andExpect(jsonPath("$.data.phone").value("01099990000"))
                .andExpect(jsonPath("$.data.address").value("서울 강남구 테헤란로 1 10층"));

        mockMvc.perform(get("/api/v1/clients/me").cookie(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("주식회사 페어링랩스"))
                .andExpect(jsonPath("$.data.phone").value("01099990000"))
                .andExpect(jsonPath("$.data.address").value("서울 강남구 테헤란로 1 10층"))
                .andExpect(jsonPath("$.data.businessNo").value("1234567890"));

        var savedAccount = accountRepository.findAll().get(0);
        org.assertj.core.api.Assertions.assertThat(savedAccount.getPhone()).isEqualTo("01099990000");
    }
}
