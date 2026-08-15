package com.pairing.support.presentation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.account.infrastructure.persistence.SpringDataAccountRepository;
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
import com.pairing.support.application.port.out.ChatbotAiPort;
import com.pairing.support.infrastructure.persistence.SpringDataChatbotMessageRepository;
import com.pairing.support.infrastructure.persistence.SpringDataChatbotQuotaRepository;
import com.pairing.support.infrastructure.persistence.SpringDataChatbotSessionRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 챗봇 질의 -> 세션/사용량/이력을 실제 요청으로 확인한다. AI 서버 호출은 {@link ChatbotAiPort}를 목으로 대체한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatbotIntegrationTest {

    private static final String WRITER_EMAIL = "chatbot-writer@pairing.com";
    private static final String OTHER_EMAIL = "chatbot-other@pairing.com";
    private static final String PASSWORD = "Passw0rd!";
    private static final String FAKE_ANSWER = "착수금 수수료는 계약 체결 시점에 발생합니다.";

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
    private SpringDataFreelancerProfileRepository freelancerProfileRepository;
    @Autowired
    private SpringDataPaymentMethodRepository paymentMethodRepository;
    @Autowired
    private SpringDataSocialAccountRepository socialAccountRepository;
    @Autowired
    private SpringDataChatbotSessionRepository chatbotSessionRepository;
    @Autowired
    private SpringDataChatbotMessageRepository chatbotMessageRepository;
    @Autowired
    private SpringDataChatbotQuotaRepository chatbotQuotaRepository;

    @MockitoBean
    private ChatbotAiPort chatbotAiPort;

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
    private Long marketingTermsId;
    private Cookie writerAccessToken;
    private Cookie otherAccessToken;

    @BeforeEach
    void setUp() throws Exception {
        chatbotMessageRepository.deleteAll();
        chatbotSessionRepository.deleteAll();
        chatbotQuotaRepository.deleteAll();
        termsAgreementRepository.deleteAll();
        paymentMethodRepository.deleteAll();
        freelancerProfileRepository.deleteAll();
        socialAccountRepository.deleteAll();
        accountRepository.deleteAll();
        termsRepository.deleteAll();

        freelancerTermsId = saveTerms(TermsCode.SERVICE, "서비스 이용약관 동의", true, "FREELANCER");
        privacyTermsId = saveTerms(TermsCode.PRIVACY_CONSENT, "개인정보 수집 및 이용 동의", true, null);
        marketingTermsId = saveTerms(TermsCode.MARKETING, "마케팅 정보 수신 동의", false, null);

        given(verifiedMarkerPort.isVerified(anyString(), any())).willReturn(true);
        given(sessionRegistryPort.isAlive(any(), anyString())).willReturn(true);
        given(chatbotAiPort.ask(anyString()))
                .willReturn(new ChatbotAiPort.Answer(FAKE_ANSWER, "RESUME_EDIT", true));

        writerAccessToken = signUpAndLoginFreelancer(WRITER_EMAIL, "이프리", "010-3333-4444", "110-123-456789");
        otherAccessToken = signUpAndLoginFreelancer(OTHER_EMAIL, "김다른", "010-5555-6666", "110-987-654321");
    }

    private Long saveTerms(TermsCode code, String title, boolean required, String targetRole) {
        return termsRepository.save(new TermsJpaEntity(
                null, code, code.getType(), "v1.0", title, "본문", required, targetRole,
                LocalDateTime.now().minusDays(1)
        )).getId();
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
        body.put("address", Map.of("sido", "서울", "sigungu", "강남구", "roadAddress", "서울 강남구 테헤란로 1", "addressDetail", "10층", "zipCode", "06234"));
        body.put("card", Map.of("cardNumber", "1234-5678-1234-5678", "cardBrand", "SHINHAN"));
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

    private Map<String, Object> askBody(Long sessionId, String question) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (sessionId != null) {
            body.put("sessionId", sessionId);
        }
        body.put("question", question);
        return body;
    }

    private MvcResult ask(Cookie token, Long sessionId, String question) throws Exception {
        return mockMvc.perform(post("/api/v1/support/chatbot/questions")
                        .cookie(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askBody(sessionId, question))))
                // 상태 검증 없이 넘어가면 10번 호출 중 실패한 호출이 조용히 묻히고, 한참 뒤
                // usedCount 검증에서야 원인 모를 실패로 터진다. 실패한 호출에서 바로 터지게 한다.
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    @DisplayName("첫 질문이면 세션이 새로 생기고 답변과 남은 횟수가 반환된다")
    void askCreatesSessionAndReturnsAnswer() throws Exception {
        mockMvc.perform(post("/api/v1/support/chatbot/questions")
                        .cookie(writerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askBody(null, "착수금 수수료는 언제 결제하나요?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").isNumber())
                .andExpect(jsonPath("$.data.question").value("착수금 수수료는 언제 결제하나요?"))
                .andExpect(jsonPath("$.data.answer").value(FAKE_ANSWER))
                // AI 가 고른 코드를 서버가 경로로 바꿔 내려준다. 프론트는 answer 를 파싱하지 않는다.
                .andExpect(jsonPath("$.data.actions.length()").value(1))
                .andExpect(jsonPath("$.data.actions[0].code").value("RESUME_EDIT"))
                .andExpect(jsonPath("$.data.actions[0].label").value("이력서 작성하러 가기"))
                .andExpect(jsonPath("$.data.actions[0].url").value("/mypage/resume"))
                .andExpect(jsonPath("$.data.remainingQuota").value(9));
    }

    @Test
    @DisplayName("AI 가 모르는 화면 코드를 주면 버튼만 빠지고 답변은 그대로 나간다")
    void unknownIntentFallsBackToNoAction() throws Exception {
        // 프롬프트로 목록을 닫아 두지만 모델이 어길 수 있다. 그때 답변까지 막히면 안 된다.
        given(chatbotAiPort.ask(anyString()))
                .willReturn(new ChatbotAiPort.Answer(FAKE_ANSWER, "GO_TO_MARS", true));

        mockMvc.perform(post("/api/v1/support/chatbot/questions")
                        .cookie(writerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askBody(null, "질문"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value(FAKE_ANSWER))
                .andExpect(jsonPath("$.data.actions.length()").value(0));
    }

    @Test
    @DisplayName("역할에 맞지 않는 화면이면 버튼만 빠진다")
    void intentNotAllowedForRoleIsDropped() throws Exception {
        // 프리랜서 계정인데 AI 가 클라이언트 전용 화면을 골랐다. 눌러도 막히는 버튼이라 뺀다.
        given(chatbotAiPort.ask(anyString()))
                .willReturn(new ChatbotAiPort.Answer(FAKE_ANSWER, "PROJECT_CREATE", true));

        mockMvc.perform(post("/api/v1/support/chatbot/questions")
                        .cookie(writerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askBody(null, "프로젝트는 어떻게 등록하나요?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value(FAKE_ANSWER))
                .andExpect(jsonPath("$.data.actions.length()").value(0));
    }

    @Test
    @DisplayName("지난 대화를 다시 불러와도 버튼이 그대로 남는다")
    void historyRestoresActions() throws Exception {
        // 버튼을 눌러 다른 화면에 갔다가 돌아오는 것이 정상 흐름이다.
        // 복원하지 않으면 버튼을 쓸수록 사라지는 화면이 된다.
        ask(writerAccessToken, null, "이력서는 어떻게 작성하나요?");

        mockMvc.perform(get("/api/v1/support/chatbot/messages").cookie(writerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].answer").value(FAKE_ANSWER))
                .andExpect(jsonPath("$.data[0].actions.length()").value(1))
                .andExpect(jsonPath("$.data[0].actions[0].code").value("RESUME_EDIT"))
                .andExpect(jsonPath("$.data[0].actions[0].url").value("/mypage/resume"));
    }

    @Test
    @DisplayName("버튼이 없던 답변은 이력에서도 버튼 없이 나온다")
    void historyKeepsNoActionAnswerEmpty() throws Exception {
        given(chatbotAiPort.ask(anyString()))
                .willReturn(new ChatbotAiPort.Answer(FAKE_ANSWER, null, true));
        ask(writerAccessToken, null, "페어링은 어떤 서비스인가요?");

        mockMvc.perform(get("/api/v1/support/chatbot/messages").cookie(writerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].actions.length()").value(0));
    }

    @Test
    @DisplayName("페어링과 무관한 질문은 하루 횟수를 차감하지 않는다")
    void outOfScopeQuestionDoesNotConsumeQuota() throws Exception {
        // AI 서버가 임베딩 유사도로 걸러 out_of_scope 로 내려준 상황.
        // 답을 못 받았는데 횟수만 빠지면 오타 한 번에 하루 10회 중 1회가 날아간다.
        given(chatbotAiPort.ask(anyString())).willReturn(
                new ChatbotAiPort.Answer("페어링 서비스 관련 질문에만 답변드릴 수 있어요.", "NONE", false));

        mockMvc.perform(post("/api/v1/support/chatbot/questions")
                        .cookie(writerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askBody(null, "1+1은?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actions.length()").value(0))
                // 차감되지 않았으므로 10회가 그대로 남아 있다
                .andExpect(jsonPath("$.data.remainingQuota").value(10));

        mockMvc.perform(get("/api/v1/support/chatbot/quota").cookie(writerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedCount").value(0));

        // 대화 자체는 남는다. 새로고침했을 때 방금 한 질문이 사라지면 그게 더 이상하다.
        mockMvc.perform(get("/api/v1/support/chatbot/messages").cookie(writerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].question").value("1+1은?"));
    }

    @Test
    @DisplayName("인사에는 답하되 하루 횟수를 차감하지 않는다")
    void greetingAnswersWithoutConsumingQuota() throws Exception {
        // 인사를 거절하면 챗봇이 고장난 것처럼 보인다. 답은 하되, 질문이 아니므로 깎지 않는다.
        String greeting = "안녕하세요! 페어링 FAQ 챗봇입니다. 궁금하신 점을 편하게 물어보세요.";
        given(chatbotAiPort.ask(anyString()))
                .willReturn(new ChatbotAiPort.Answer(greeting, "NONE", false));

        mockMvc.perform(post("/api/v1/support/chatbot/questions")
                        .cookie(writerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askBody(null, "반가워"))))
                .andExpect(status().isOk())
                // 거절 문구가 아니라 인사 답변이 그대로 나가야 한다.
                .andExpect(jsonPath("$.data.answer").value(greeting))
                .andExpect(jsonPath("$.data.remainingQuota").value(10));

        mockMvc.perform(get("/api/v1/support/chatbot/quota").cookie(writerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedCount").value(0));
    }

    @Test
    @DisplayName("같은 세션으로 이어서 물으면 세션이 재사용되고 남은 횟수가 계속 줄어든다")
    void askContinuesExistingSession() throws Exception {
        MvcResult first = ask(writerAccessToken, null, "첫 질문");
        Long sessionId = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("sessionId").asLong();

        mockMvc.perform(post("/api/v1/support/chatbot/questions")
                        .cookie(writerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askBody(sessionId, "두번째 질문"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.remainingQuota").value(8));
    }

    @Test
    @DisplayName("다른 사람의 세션으로 질문하면 403, 없는 세션이면 404다")
    void askRejectsUnownedOrUnknownSession() throws Exception {
        MvcResult created = ask(writerAccessToken, null, "첫 질문");
        Long sessionId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("sessionId").asLong();

        mockMvc.perform(post("/api/v1/support/chatbot/questions")
                        .cookie(otherAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askBody(sessionId, "남의 세션"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CB_002"));

        mockMvc.perform(post("/api/v1/support/chatbot/questions")
                        .cookie(writerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askBody(999999L, "없는 세션"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CB_001"));
    }

    @Test
    @DisplayName("하루 한도를 넘기면 429이고, 그 전까지는 한도 조회에 정확히 반영된다")
    void quotaExceededReturns429() throws Exception {
        mockMvc.perform(get("/api/v1/support/chatbot/quota").cookie(writerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedCount").value(0))
                .andExpect(jsonPath("$.data.remainingCount").value(10));

        for (int i = 0; i < 10; i++) {
            ask(writerAccessToken, null, "질문 " + i);
        }

        mockMvc.perform(get("/api/v1/support/chatbot/quota").cookie(writerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedCount").value(10))
                .andExpect(jsonPath("$.data.remainingCount").value(0));

        mockMvc.perform(post("/api/v1/support/chatbot/questions")
                        .cookie(writerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askBody(null, "한도 초과 질문"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("CB_003"));

        // 다른 계정은 별도로 한도가 관리된다.
        mockMvc.perform(get("/api/v1/support/chatbot/quota").cookie(otherAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedCount").value(0));
    }

    @Test
    @DisplayName("오늘 대화 이력이 시간순으로 조회되고, 남의 대화는 섞이지 않는다")
    void findTodayMessagesReturnsHistoryInOrderAndOnlyMine() throws Exception {
        MvcResult created = ask(writerAccessToken, null, "첫 질문");
        Long sessionId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("sessionId").asLong();
        ask(writerAccessToken, sessionId, "두번째 질문");
        ask(otherAccessToken, null, "남의 질문");

        mockMvc.perform(get("/api/v1/support/chatbot/messages").cookie(writerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].question").value("첫 질문"))
                .andExpect(jsonPath("$.data[1].question").value("두번째 질문"));

        mockMvc.perform(get("/api/v1/support/chatbot/messages").cookie(otherAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].question").value("남의 질문"));
    }

    @Test
    @DisplayName("세션이 여러 개로 나뉘어도 오늘 대화는 한 흐름으로 모아서 조회된다")
    void findTodayMessagesMergesSeparateSessions() throws Exception {
        // sessionId 없이 물으면 세션이 새로 생긴다. 화면을 새로 열어 다시 묻는 상황이다.
        ask(writerAccessToken, null, "첫 세션 질문");
        ask(writerAccessToken, null, "새 세션 질문");

        mockMvc.perform(get("/api/v1/support/chatbot/messages").cookie(writerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].question").value("첫 세션 질문"))
                .andExpect(jsonPath("$.data[1].question").value("새 세션 질문"))
                // 이어서 물을 때 쓰라고 sessionId 를 항목마다 같이 준다.
                .andExpect(jsonPath("$.data[0].sessionId").isNumber())
                .andExpect(jsonPath("$.data[1].sessionId").isNumber());
    }

    @Test
    @DisplayName("대화한 적이 없으면 빈 목록이다")
    void findTodayMessagesReturnsEmptyWhenNothingAsked() throws Exception {
        mockMvc.perform(get("/api/v1/support/chatbot/messages").cookie(writerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
