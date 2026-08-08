package com.pairing.notification.presentation.api;

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
import com.pairing.notification.application.command.CreateNotificationCommand;
import com.pairing.notification.application.event.NotificationEvent;
import com.pairing.notification.application.port.out.NotificationEventPort;
import com.pairing.notification.application.usecase.NotificationCreateUseCase;
import com.pairing.notification.domain.model.NotificationType;
import com.pairing.notification.infrastructure.persistence.SpringDataNotificationRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 알림 생성(내부 포트) -> 목록/안읽음수/읽음/삭제를 실제 요청으로 확인한다.
 *
 * <p>알림 생성은 REST로 노출되지 않고 다른 도메인이 {@code NotificationCreateUseCase} 를 직접
 * 호출하는 구조라, 테스트에서도 같은 방식으로(내부 포트 직접 호출) 알림을 심는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationIntegrationTest {

    private static final String CLIENT_EMAIL = "notification-client@pairing.com";
    private static final String FREELANCER_EMAIL = "notification-freelancer@pairing.com";
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
    private SpringDataNotificationRepository notificationRepository;
    @Autowired
    private NotificationCreateUseCase notificationCreateUseCase;

    @MockitoBean
    private NotificationEventPort notificationEventPort;

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

    @BeforeEach
    void setUp() throws Exception {
        notificationRepository.deleteAll();
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
    }

    private Long saveTerms(TermsCode code, String title, boolean required, String targetRole) {
        return termsRepository.save(new TermsJpaEntity(
                null, code, code.getType(), "v1.0", title, "본문", required, targetRole,
                LocalDateTime.now().minusDays(1)
        )).getId();
    }

    private void signUpAndLoginClient() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("companyName", "주식회사 페어링");
        body.put("businessNo", "1234567890");
        body.put("businessField", "IT_CONTENTS_AI");
        body.put("employeeCount", "SIZE_10_49");
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

    private Long createNotification(Long ownerAccountId, String title) {
        return notificationCreateUseCase.create(new CreateNotificationCommand(ownerAccountId,
                NotificationType.MATCHING_REQUESTED, title, "내용", "/matchings/requests/1")).notificationId();
    }

    @Test
    @DisplayName("알림이 생성되면 목록·안읽음수에 반영되고 실시간 push 포트가 호출된다")
    void createReflectsInListAndUnreadCountAndPushes() throws Exception {
        Long notificationId = createNotification(freelancerAccountId, "매칭 요청이 도착했습니다");

        verify(notificationEventPort).publish(eq(freelancerAccountId), any(NotificationEvent.class));

        mockMvc.perform(get("/api/v1/notifications").cookie(freelancerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].notificationId").value(notificationId))
                .andExpect(jsonPath("$.data.content[0].title").value("매칭 요청이 도착했습니다"))
                .andExpect(jsonPath("$.data.content[0].read").value(false));

        mockMvc.perform(get("/api/v1/notifications/unread-count").cookie(freelancerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    @DisplayName("단건 읽음 처리하면 안읽음수가 줄고 unreadOnly 필터에서 빠진다")
    void markAsReadUpdatesUnreadCountAndFilter() throws Exception {
        Long notificationId = createNotification(freelancerAccountId, "매칭 요청이 도착했습니다");
        createNotification(freelancerAccountId, "새로운 AI 제안");

        mockMvc.perform(put("/api/v1/notifications/" + notificationId + "/read").cookie(freelancerAccessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications/unread-count").cookie(freelancerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        mockMvc.perform(get("/api/v1/notifications").param("unreadOnly", "true").cookie(freelancerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("새로운 AI 제안"));
    }

    @Test
    @DisplayName("모두 읽음 처리하면 안읽음수가 0이 된다")
    void markAllAsReadClearsUnreadCount() throws Exception {
        createNotification(freelancerAccountId, "매칭 요청이 도착했습니다");
        createNotification(freelancerAccountId, "새로운 AI 제안");

        mockMvc.perform(put("/api/v1/notifications/read-all").cookie(freelancerAccessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications/unread-count").cookie(freelancerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    @DisplayName("단건 삭제와 전체 삭제가 실제로 목록에서 제거한다")
    void deleteAndDeleteAllRemoveNotifications() throws Exception {
        Long notificationId = createNotification(freelancerAccountId, "매칭 요청이 도착했습니다");
        createNotification(freelancerAccountId, "새로운 AI 제안");

        mockMvc.perform(delete("/api/v1/notifications/" + notificationId).cookie(freelancerAccessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications").cookie(freelancerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));

        mockMvc.perform(delete("/api/v1/notifications").cookie(freelancerAccessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications").cookie(freelancerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @DisplayName("다른 사람의 알림은 읽음 처리도 삭제도 할 수 없다")
    void cannotTouchAnotherAccountsNotification() throws Exception {
        Long clientNotificationId = createNotification(clientAccountId, "계약서가 생성되었습니다");

        mockMvc.perform(put("/api/v1/notifications/" + clientNotificationId + "/read")
                        .cookie(freelancerAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NT_002"));

        mockMvc.perform(delete("/api/v1/notifications/" + clientNotificationId)
                        .cookie(freelancerAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NT_002"));
    }

    @Test
    @DisplayName("존재하지 않는 알림을 처리하려 하면 404다")
    void unknownNotificationReturnsNotFound() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/999999/read").cookie(freelancerAccessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NT_001"));
    }
}
