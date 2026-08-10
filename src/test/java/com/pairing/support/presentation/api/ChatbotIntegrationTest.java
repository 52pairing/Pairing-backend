//package com.pairing.support.presentation.api;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.pairing.account.infrastructure.persistence.SpringDataAccountRepository;
//import com.pairing.account.infrastructure.persistence.SpringDataFreelancerProfileRepository;
//import com.pairing.account.infrastructure.persistence.SpringDataPaymentMethodRepository;
//import com.pairing.account.infrastructure.persistence.SpringDataSocialAccountRepository;
//import com.pairing.auth.application.port.AccountSuspensionPort;
//import com.pairing.auth.application.port.EmailSendLimitPort;
//import com.pairing.auth.application.port.LoginAttemptPort;
//import com.pairing.auth.application.port.MailSenderPort;
//import com.pairing.auth.application.port.OAuthStatePort;
//import com.pairing.auth.application.port.PasswordResetTokenPort;
//import com.pairing.auth.application.port.SessionRegistryPort;
//import com.pairing.auth.application.port.SignUpTicketPort;
//import com.pairing.auth.application.port.TokenStorePort;
//import com.pairing.auth.application.port.VerifiedMarkerPort;
//import com.pairing.support.application.port.out.ChatbotAiPort;
//import com.pairing.support.infrastructure.persistence.SpringDataChatbotMessageRepository;
//import com.pairing.support.infrastructure.persistence.SpringDataChatbotQuotaRepository;
//import com.pairing.support.infrastructure.persistence.SpringDataChatbotSessionRepository;
//import com.pairing.terms.domain.model.TermsCode;
//import com.pairing.terms.infrastructure.persistence.SpringDataTermsAgreementRepository;
//import com.pairing.terms.infrastructure.persistence.SpringDataTermsRepository;
//import com.pairing.terms.infrastructure.persistence.TermsJpaEntity;
//import jakarta.servlet.http.Cookie;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.http.MediaType;
//import org.springframework.test.context.bean.override.mockito.MockitoBean;
//import org.springframework.test.web.servlet.MockMvc;
//import org.springframework.test.web.servlet.MvcResult;
//
//import java.time.LocalDateTime;
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.BDDMockito.given;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
///**
// * 챗봇 질의 -> 세션/사용량/이력을 실제 요청으로 확인한다. AI 서버 호출은 {@link ChatbotAiPort}를 목으로 대체한다.
// */
//@SpringBootTest
//@AutoConfigureMockMvc
//class ChatbotIntegrationTest {
//
//    private static final String WRITER_EMAIL = "chatbot-writer@pairing.com";
//    private static final String OTHER_EMAIL = "chatbot-other@pairing.com";
//    private static final String PASSWORD = "Passw0rd!";
//    private static final String FAKE_ANSWER = "착수금 수수료는 계약 체결 시점에 발생합니다.";
//
//    @Autowired
//    private MockMvc mockMvc;
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @Autowired
//    private SpringDataTermsRepository termsRepository;
//    @Autowired
//    private SpringDataTermsAgreementRepository termsAgreementRepository;
//    @Autowired
//    private SpringDataAccountRepository accountRepository;
//    @Autowired
//    private SpringDataFreelancerProfileRepository freelancerProfileRepository;
//    @Autowired
//    private SpringDataPaymentMethodRepository paymentMethodRepository;
//    @Autowired
//    private SpringDataSocialAccountRepository socialAccountRepository;
//    @Autowired
//    private SpringDataChatbotSessionRepository chatbotSessionRepository;
//    @Autowired
//    private SpringDataChatbotMessageRepository chatbotMessageRepository;
//    @Autowired
//    private SpringDataChatbotQuotaRepository chatbotQuotaRepository;
//
//    @MockitoBean
//    private ChatbotAiPort chatbotAiPort;
//
//    @MockitoBean
//    private VerifiedMarkerPort verifiedMarkerPort;
//    @MockitoBean
//    private TokenStorePort tokenStorePort;
//    @MockitoBean
//    private SessionRegistryPort sessionRegistryPort;
//    @MockitoBean
//    private LoginAttemptPort loginAttemptPort;
//    @MockitoBean
//    private AccountSuspensionPort accountSuspensionPort;
//    @MockitoBean
//    private EmailSendLimitPort emailSendLimitPort;
//    @MockitoBean
//    private MailSenderPort mailSenderPort;
//    @MockitoBean
//    private SignUpTicketPort signUpTicketPort;
//    @MockitoBean
//    private OAuthStatePort oAuthStatePort;
//    @MockitoBean
//    private PasswordResetTokenPort passwordResetTokenPort;
//
//    private Long freelancerTermsId;
//    private Long privacyTermsId;
//    private Long marketingTermsId;
//    private Cookie writerAccessToken;
//    private Cookie otherAccessToken;
//
//    @BeforeEach
//    void setUp() throws Exception {
//        chatbotMessageRepository.deleteAll();
//        chatbotSessionRepository.deleteAll();
//        chatbotQuotaRepository.deleteAll();
//        termsAgreementRepository.deleteAll();
//        paymentMethodRepository.deleteAll();
//        freelancerProfileRepository.deleteAll();
//        socialAccountRepository.deleteAll();
//        accountRepository.deleteAll();
//        termsRepository.deleteAll();
//
//        freelancerTermsId = saveTerms(TermsCode.SERVICE, "서비스 이용약관 동의", true, "FREELANCER");
//        privacyTermsId = saveTerms(TermsCode.PRIVACY_CONSENT, "개인정보 수집 및 이용 동의", true, null);
//        marketingTermsId = saveTerms(TermsCode.MARKETING, "마케팅 정보 수신 동의", false, null);
//
//        given(verifiedMarkerPort.isVerified(anyString(), any())).willReturn(true);
//        given(sessionRegistryPort.isAlive(any(), anyString())).willReturn(true);
//        given(chatbotAiPort.ask(anyString())).willReturn(FAKE_ANSWER);
//
//        writerAccessToken = signUpAndLoginFreelancer(WRITER_EMAIL, "이프리", "010-3333-4444", "110-123-456789");
//        otherAccessToken = signUpAndLoginFreelancer(OTHER_EMAIL, "김다른", "010-5555-6666", "110-987-654321");
//    }
//
//    private Long saveTerms(TermsCode code, String title, boolean required, String targetRole) {
//        return termsRepository.save(new TermsJpaEntity(
//                null, code, code.getType(), "v1.0", title, "본문", required, targetRole,
//                LocalDateTime.now().minusDays(1)
//        )).getId();
//    }
//
//    private Cookie signUpAndLoginFreelancer(String email, String name, String phone, String bankAccountNo)
//            throws Exception {
//        Map<String, Object> body = new LinkedHashMap<>();
//        body.put("name", name);
//        body.put("phone", phone);
//        body.put("email", email);
//        body.put("password", PASSWORD);
//        body.put("passwordConfirm", PASSWORD);
//        body.put("birthDate", "1995-03-01");
//        body.put("card", Map.of("cardNumber", "1234-5678-1234-5678", "cardBrand", "신한카드"));
//        body.put("bankAccount", Map.of("bankCode", "088", "accountNo", bankAccountNo, "accountHolder", name));
//        body.put("agreements", List.of(
//                Map.of("termsId", freelancerTermsId, "agreed", true),
//                Map.of("termsId", privacyTermsId, "agreed", true),
//                Map.of("termsId", marketingTermsId, "agreed", true)));
//
//        mockMvc.perform(post("/api/v1/auth/signup/freelancer")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(body)))
//                .andExpect(status().isCreated());
//
//        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content("""
//                                {"email":"%s","password":"%s","role":"FREELANCER"}"""
//                                .formatted(email, PASSWORD)))
//                .andExpect(status().isOk())
//                .andReturn();
//
//        return loginResult.getResponse().getCookie("accessToken");
//    }
//
//    private Map<String, Object> askBody(Long sessionId, String question) {
//        Map<String, Object> body = new LinkedHashMap<>();
//        if (sessionId != null) {
//            body.put("sessionId", sessionId);
//        }
//        body.put("question", question);
//        return body;
//    }
//
//    private MvcResult ask(Cookie token, Long sessionId, String question) throws Exception {
//        return mockMvc.perform(post("/api/v1/support/chatbot/questions")
//                        .cookie(token)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(askBody(sessionId, question))))
//                .andReturn();
//    }
//
//    @Test
//    @DisplayName("첫 질문이면 세션이 새로 생기고 답변과 남은 횟수가 반환된다")
//    void askCreatesSessionAndReturnsAnswer() throws Exception {
//        mockMvc.perform(post("/api/v1/support/chatbot/questions")
//                        .cookie(writerAccessToken)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(askBody(null, "착수금 수수료는 언제 결제하나요?"))))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.data.sessionId").isNumber())
//                .andExpect(jsonPath("$.data.question").value("착수금 수수료는 언제 결제하나요?"))
//                .andExpect(jsonPath("$.data.answer").value(FAKE_ANSWER))
//                .andExpect(jsonPath("$.data.remainingQuota").value(9));
//    }
//
//    @Test
//    @DisplayName("같은 세션으로 이어서 물으면 세션이 재사용되고 남은 횟수가 계속 줄어든다")
//    void askContinuesExistingSession() throws Exception {
//        MvcResult first = ask(writerAccessToken, null, "첫 질문");
//        Long sessionId = objectMapper.readTree(first.getResponse().getContentAsString())
//                .path("data").path("sessionId").asLong();
//
//        mockMvc.perform(post("/api/v1/support/chatbot/questions")
//                        .cookie(writerAccessToken)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(askBody(sessionId, "두번째 질문"))))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
//                .andExpect(jsonPath("$.data.remainingQuota").value(8));
//    }
//
//    @Test
//    @DisplayName("다른 사람의 세션으로 질문하면 403, 없는 세션이면 404다")
//    void askRejectsUnownedOrUnknownSession() throws Exception {
//        MvcResult created = ask(writerAccessToken, null, "첫 질문");
//        Long sessionId = objectMapper.readTree(created.getResponse().getContentAsString())
//                .path("data").path("sessionId").asLong();
//
//        mockMvc.perform(post("/api/v1/support/chatbot/questions")
//                        .cookie(otherAccessToken)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(askBody(sessionId, "남의 세션"))))
//                .andExpect(status().isForbidden())
//                .andExpect(jsonPath("$.errorCode").value("CB_002"));
//
//        mockMvc.perform(post("/api/v1/support/chatbot/questions")
//                        .cookie(writerAccessToken)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(askBody(999999L, "없는 세션"))))
//                .andExpect(status().isNotFound())
//                .andExpect(jsonPath("$.errorCode").value("CB_001"));
//    }
//
//    @Test
//    @DisplayName("하루 한도를 넘기면 429이고, 그 전까지는 한도 조회에 정확히 반영된다")
//    void quotaExceededReturns429() throws Exception {
//        mockMvc.perform(get("/api/v1/support/chatbot/quota").cookie(writerAccessToken))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.data.usedCount").value(0))
//                .andExpect(jsonPath("$.data.remainingCount").value(10));
//
//        for (int i = 0; i < 10; i++) {
//            ask(writerAccessToken, null, "질문 " + i);
//        }
//
//        mockMvc.perform(get("/api/v1/support/chatbot/quota").cookie(writerAccessToken))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.data.usedCount").value(10))
//                .andExpect(jsonPath("$.data.remainingCount").value(0));
//
//        mockMvc.perform(post("/api/v1/support/chatbot/questions")
//                        .cookie(writerAccessToken)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(askBody(null, "한도 초과 질문"))))
//                .andExpect(status().isTooManyRequests())
//                .andExpect(jsonPath("$.errorCode").value("CB_003"));
//
//        // 다른 계정은 별도로 한도가 관리된다.
//        mockMvc.perform(get("/api/v1/support/chatbot/quota").cookie(otherAccessToken))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.data.usedCount").value(0));
//    }
//
//    @Test
//    @DisplayName("세션의 대화 이력이 시간순으로 조회되고, 본인이 아니면 볼 수 없다")
//    void findMessagesReturnsHistoryInOrderAndRestrictsOwnership() throws Exception {
//        MvcResult created = ask(writerAccessToken, null, "첫 질문");
//        Long sessionId = objectMapper.readTree(created.getResponse().getContentAsString())
//                .path("data").path("sessionId").asLong();
//        ask(writerAccessToken, sessionId, "두번째 질문");
//
//        mockMvc.perform(get("/api/v1/support/chatbot/sessions/" + sessionId + "/messages")
//                        .cookie(writerAccessToken))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.data.length()").value(2))
//                .andExpect(jsonPath("$.data[0].question").value("첫 질문"))
//                .andExpect(jsonPath("$.data[1].question").value("두번째 질문"));
//
//        mockMvc.perform(get("/api/v1/support/chatbot/sessions/" + sessionId + "/messages")
//                        .cookie(otherAccessToken))
//                .andExpect(status().isForbidden())
//                .andExpect(jsonPath("$.errorCode").value("CB_002"));
//    }
//}
