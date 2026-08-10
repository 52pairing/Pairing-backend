package com.pairing.freelancer.presentation.api;

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
import com.pairing.file.domain.model.FilePurpose;
import com.pairing.file.infrastructure.persistence.FileJpaEntity;
import com.pairing.file.infrastructure.persistence.SpringDataFileRepository;
import com.pairing.freelancer.infrastructure.persistence.SpringDataFreelancerConditionRepository;
import com.pairing.freelancer.infrastructure.persistence.SpringDataResumeRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 프리랜서 조건·이력서를 실제 요청으로 저장하고 다시 읽어 왕복을 확인한다.
 *
 * <p>오늘 새로 구현한 부분(JPA 하위 컬렉션 매핑, 계정 연락처 fallback)이 실제로 동작하는지
 * 스텁이 아니라 H2 DB에 저장된 값을 읽어 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FreelancerMyPageIntegrationTest {

    private static final String EMAIL = "freelancer-mypage@pairing.com";
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
    @Autowired
    private SpringDataFreelancerConditionRepository freelancerConditionRepository;
    @Autowired
    private SpringDataResumeRepository resumeRepository;
    @Autowired
    private SpringDataFileRepository fileRepository;

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
    private Cookie accessToken;
    private Long profileImageFileId;
    private Long portfolioFileId;

    @BeforeEach
    void setUp() throws Exception {
        resumeRepository.deleteAll();
        freelancerConditionRepository.deleteAll();
        termsAgreementRepository.deleteAll();
        paymentMethodRepository.deleteAll();
        clientProfileRepository.deleteAll();
        freelancerProfileRepository.deleteAll();
        socialAccountRepository.deleteAll();
        accountRepository.deleteAll();
        termsRepository.deleteAll();
        fileRepository.deleteAll();

        freelancerTermsId = saveTerms(TermsCode.SERVICE, "서비스 이용약관 동의", true, "FREELANCER");
        privacyTermsId = saveTerms(TermsCode.PRIVACY_CONSENT, "개인정보 수집 및 이용 동의", true, null);
        marketingTermsId = saveTerms(TermsCode.MARKETING, "마케팅 정보 수신 동의", false, null);

        given(verifiedMarkerPort.isVerified(anyString(), any())).willReturn(true);
        given(sessionRegistryPort.isAlive(any(), anyString())).willReturn(true);

        // 실제 S3 업로드 없이, file 도메인이 이미 메타를 갖고 있는 상태를 가정한다
        // (fileId -> object key 조회 로직 자체를 검증하는 게 목적).
        profileImageFileId = fileRepository.save(new FileJpaEntity(null, 1L, FilePurpose.PROFILE_IMAGE,
                "profile_image/test-photo.png", "photo.png", "image/png", 2048L, LocalDateTime.now())).getId();
        portfolioFileId = fileRepository.save(new FileJpaEntity(null, 1L, FilePurpose.PORTFOLIO,
                "portfolio/test-portfolio.pdf", "portfolio.pdf", "application/pdf", 4096L, LocalDateTime.now()))
                .getId();

        signUpAndLoginFreelancer();
    }

    private Long saveTerms(TermsCode code, String title, boolean required, String targetRole) {
        return termsRepository.save(new TermsJpaEntity(
                null, code, code.getType(), "v1.0", title, "본문", required, targetRole,
                LocalDateTime.now().minusDays(1)
        )).getId();
    }

    private void signUpAndLoginFreelancer() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "홍길동");
        body.put("phone", ACCOUNT_PHONE);
        body.put("email", EMAIL);
        body.put("password", PASSWORD);
        body.put("passwordConfirm", PASSWORD);
        body.put("birthDate", "1995-03-01");
        body.put("card", Map.of("cardNumber", "1234-5678-1234-5678", "cardBrand", "신한카드"));
        body.put("bankAccount", Map.of("bankCode", "088", "accountNo", "110-123-456789", "accountHolder", "홍길동"));
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
                                .formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        accessToken = loginResult.getResponse().getCookie("accessToken");
    }

    private Map<String, Object> conditionBody(Integer periodValue) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobCategory", "DEVELOPMENT");
        body.put("jobRole", "BACKEND");
        body.put("affiliation", "프리랜서");
        body.put("workStyle", "REMOTE");
        body.put("workForm", "FULL_TIME");
        body.put("payUnit", "MONTHLY");
        body.put("payAmount", 5_000_000);
        body.put("minAcceptAmount", 4_000_000);
        body.put("availableFrom", "2026-09-01");
        body.put("startNegotiable", true);
        body.put("periodValue", periodValue);
        body.put("periodUnit", "MONTH");
        body.put("hasFreelanceExperience", true);
        body.put("careerYears", 5);
        body.put("skills", List.of(Map.of("skillCode", "JAVA", "skillLevel", "ADVANCED")));
        return body;
    }

    @Test
    @DisplayName("조건을 저장하고 다시 조회하면 저장한 값이 그대로 나온다")
    void conditionRoundTrip() throws Exception {
        mockMvc.perform(put("/api/v1/freelancers/me/condition")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conditionBody(6))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobRole").value("BACKEND"))
                .andExpect(jsonPath("$.data.periodValue").value(6))
                .andExpect(jsonPath("$.data.skills[0].skillCode").value("JAVA"));

        mockMvc.perform(get("/api/v1/freelancers/me/condition").cookie(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobCategory").value("DEVELOPMENT"))
                .andExpect(jsonPath("$.data.payAmount").value(5_000_000))
                .andExpect(jsonPath("$.data.minAcceptAmount").value(4_000_000))
                .andExpect(jsonPath("$.data.periodValue").value(6));
    }

    @Test
    @DisplayName("협의 가능이라 기간 값을 비우면 null로 저장되고 조회에도 null로 나온다")
    void conditionPeriodValueCanBeNull() throws Exception {
        mockMvc.perform(put("/api/v1/freelancers/me/condition")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conditionBody(null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.periodValue").doesNotExist());

        mockMvc.perform(get("/api/v1/freelancers/me/condition").cookie(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.periodValue").doesNotExist());
    }

    @Test
    @DisplayName("등록 전에는 조건 조회 data가 null이다")
    void conditionIsNullBeforeRegistration() throws Exception {
        mockMvc.perform(get("/api/v1/freelancers/me/condition").cookie(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private Map<String, Object> resumeBody(String contactPhone) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("profileFileId", profileImageFileId);
        body.put("contactPhone", contactPhone);
        body.put("contactEmail", "");
        body.put("address", "서울 강남구");
        body.put("selfIntroduction", "백엔드 5년차입니다.");
        body.put("portfolioFileId", portfolioFileId);
        body.put("educations", List.of(Map.of(
                "startDate", "2014-03-01", "endDate", "2018-02-28",
                "schoolName", "페어링대학교", "major", "컴퓨터공학",
                "graduationStatus", "GRADUATED", "campusType", "MAIN")));
        body.put("careers", List.of(Map.of(
                "startDate", "2018-03-01", "endDate", "2023-02-28",
                "companyName", "주식회사 예시", "departmentRank", "서버개발팀 대리",
                "jobDescription", "결제 시스템 개발")));
        body.put("certificates", List.of(Map.of(
                "acquiredDate", "2020-05-01", "name", "정보처리기사", "issuerScore", "한국산업인력공단")));
        body.put("links", List.of(Map.of("url", "https://github.com/pairing")));
        body.put("agreements", Map.of(
                "profileCollectionAgreed", true, "profileProvisionAgreed", true,
                "aiAnalysisAgreed", true, "careerPortfolioUsageAgreed", true));
        return body;
    }

    @Test
    @DisplayName("이력서를 저장하고 다시 조회하면 학력·경력·자격증·링크가 그대로 나온다")
    void resumeRoundTrip() throws Exception {
        mockMvc.perform(put("/api/v1/freelancers/me/resume")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resumeBody("010-9999-8888"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.contactPhone").value("010-9999-8888"))
                .andExpect(jsonPath("$.data.educations[0].schoolName").value("페어링대학교"))
                .andExpect(jsonPath("$.data.careers[0].companyName").value("주식회사 예시"))
                .andExpect(jsonPath("$.data.certificates[0].name").value("정보처리기사"))
                .andExpect(jsonPath("$.data.links[0]").value("https://github.com/pairing"))
                .andExpect(jsonPath("$.data.profileImageUrl", org.hamcrest.Matchers.endsWith(
                        "profile_image/test-photo.png")))
                .andExpect(jsonPath("$.data.portfolioUrl", org.hamcrest.Matchers.endsWith(
                        "portfolio/test-portfolio.pdf")));

        mockMvc.perform(get("/api/v1/freelancers/me/resume").cookie(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.condition").doesNotExist())
                .andExpect(jsonPath("$.data.resume.address").value("서울 강남구"))
                .andExpect(jsonPath("$.data.resume.educations[0].major").value("컴퓨터공학"))
                .andExpect(jsonPath("$.data.resume.careers[0].jobDescription").value("결제 시스템 개발"))
                .andExpect(jsonPath("$.data.resume.profileImageUrl", org.hamcrest.Matchers.endsWith(
                        "profile_image/test-photo.png")));
    }

    @Test
    @DisplayName("연락처를 비우면 계정 전화번호를 그대로 보여준다")
    void resumeFallsBackToAccountContactWhenBlank() throws Exception {
        mockMvc.perform(put("/api/v1/freelancers/me/resume")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resumeBody(""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactPhone").value(ACCOUNT_PHONE.replace("-", "")));
    }

    @Test
    @DisplayName("학력사항이 없으면 400으로 막는다")
    void resumeWithoutEducationIsRejected() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(resumeBody("010-9999-8888"));
        body.put("educations", List.of());

        mockMvc.perform(put("/api/v1/freelancers/me/resume")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("가입만 하고 이력서를 채우지 않으면 매칭 설정은 동의 상태여도 이력서 미완성으로 매칭 대상이 아니다")
    void matchingSettingsIsNotMatchableWithoutResume() throws Exception {
        mockMvc.perform(get("/api/v1/freelancers/me/matching-settings").cookie(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiMatchingAgreed").value(true))
                .andExpect(jsonPath("$.data.matchingPaused").value(false))
                .andExpect(jsonPath("$.data.matchable").value(false))
                .andExpect(jsonPath("$.data.unmatchableReason").value("이력서를 완성해야 추천 대상에 포함됩니다."));
    }

    @Test
    @DisplayName("이력서까지 채우면 매칭 설정 조회에서 matchable이 true가 된다")
    void matchingSettingsIsMatchableOnceResumeIsCompleted() throws Exception {
        mockMvc.perform(put("/api/v1/freelancers/me/resume")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resumeBody("010-9999-8888"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/freelancers/me/matching-settings").cookie(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchable").value(true))
                .andExpect(jsonPath("$.data.unmatchableReason").doesNotExist());
    }

    @Test
    @DisplayName("매칭 설정을 저장하면 실제로 DB에 반영되고, 다시 조회해도 저장한 값이 그대로 나온다")
    void matchingSettingsUpdateIsPersisted() throws Exception {
        mockMvc.perform(put("/api/v1/freelancers/me/resume")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resumeBody("010-9999-8888"))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/freelancers/me/matching-settings")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"aiMatchingAgreed":true,"matchingPaused":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiMatchingAgreed").value(true))
                .andExpect(jsonPath("$.data.matchingPaused").value(true))
                .andExpect(jsonPath("$.data.matchable").value(false))
                .andExpect(jsonPath("$.data.unmatchableReason").value("매칭을 재개해야 추천 대상에 포함됩니다."));

        var savedProfile = freelancerProfileRepository.findAll().get(0);
        org.assertj.core.api.Assertions.assertThat(savedProfile.isMatchingPaused()).isTrue();
        org.assertj.core.api.Assertions.assertThat(savedProfile.isAiMatchingAgreed()).isTrue();

        mockMvc.perform(get("/api/v1/freelancers/me/matching-settings").cookie(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchingPaused").value(true))
                .andExpect(jsonPath("$.data.matchable").value(false));
    }

    @Test
    @DisplayName("AI 매칭 동의를 끄면 이력서를 완성했어도 매칭 대상이 아니고, 동의 미비 사유가 우선한다")
    void matchingSettingsAiMatchingNotAgreedTakesPriorityOverResume() throws Exception {
        mockMvc.perform(put("/api/v1/freelancers/me/resume")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resumeBody("010-9999-8888"))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/freelancers/me/matching-settings")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"aiMatchingAgreed":false,"matchingPaused":false}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchable").value(false))
                .andExpect(jsonPath("$.data.unmatchableReason").value("AI 매칭에 동의해야 추천 대상에 포함됩니다."));

        var savedProfile = freelancerProfileRepository.findAll().get(0);
        org.assertj.core.api.Assertions.assertThat(savedProfile.isAiMatchingAgreed()).isFalse();
    }
}
