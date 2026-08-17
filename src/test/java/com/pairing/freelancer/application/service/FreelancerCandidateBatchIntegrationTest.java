package com.pairing.freelancer.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.account.application.usecase.AccountQueryUseCase;
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
import com.pairing.freelancer.application.result.FreelancerCandidateSummaryResult;
import com.pairing.freelancer.application.result.ResumeResult;
import com.pairing.freelancer.application.usecase.FreelancerCandidateSummaryUseCase;
import com.pairing.freelancer.application.usecase.ResumeUseCase;
import com.pairing.freelancer.infrastructure.persistence.SpringDataFreelancerConditionRepository;
import com.pairing.freelancer.infrastructure.persistence.SpringDataResumeDraftRepository;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * getSummaries()/findResumes() 배치 경로가 계정마다 되묻던 것과 같은 결과를 내는지 확인한다.
 *
 * <p>두 프리랜서가 서로 다른 프로필 이미지를 쓰게 해서, 배치 결과를 다시 조립하는 과정에서
 * 한 사람의 값이 다른 사람에게 섞여 들어가는 실수(맵 키 오배정)를 잡는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FreelancerCandidateBatchIntegrationTest {

    private static final String PASSWORD = "Passw0rd!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private FreelancerCandidateSummaryUseCase freelancerCandidateSummaryUseCase;
    @Autowired
    private ResumeUseCase resumeUseCase;
    @Autowired
    private AccountQueryUseCase accountQueryUseCase;

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
    private SpringDataResumeDraftRepository resumeDraftRepository;
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

    @BeforeEach
    void setUp() {
        resumeDraftRepository.deleteAll();
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
    }

    private Long saveTerms(TermsCode code, String title, boolean required, String targetRole) {
        return termsRepository.save(new TermsJpaEntity(
                null, code, code.getType(), "v1.0", title, "본문", required, targetRole,
                LocalDateTime.now().minusDays(1)
        )).getId();
    }

    private record Freelancer(Long accountId, Long freelancerProfileId, Cookie accessToken, Long profileFileId) {
    }

    private Freelancer signUpFreelancerWithResume(String email, String name, String birthDate,
                                                  String selfIntroduction) throws Exception {
        Long profileFileId = fileRepository.save(new FileJpaEntity(null, 1L, FilePurpose.PROFILE_IMAGE,
                "profile_image/" + email + ".png", "photo.png", "image/png", 2048L, LocalDateTime.now()))
                .getId();
        Long portfolioFileId = fileRepository.save(new FileJpaEntity(null, 1L, FilePurpose.PORTFOLIO,
                "portfolio/" + email + ".pdf", "portfolio.pdf", "application/pdf", 4096L, LocalDateTime.now()))
                .getId();

        Map<String, Object> signup = new LinkedHashMap<>();
        signup.put("name", name);
        signup.put("phone", "010-" + (1000 + profileFileId) + "-0000");
        signup.put("email", email);
        signup.put("password", PASSWORD);
        signup.put("passwordConfirm", PASSWORD);
        signup.put("birthDate", birthDate);
        signup.put("address", Map.of("sido", "서울", "sigungu", "강남구",
                "roadAddress", "서울 강남구 테헤란로 1", "addressDetail", "10층", "zipCode", "06234"));
        signup.put("card", Map.of("cardNumber", "1234-5678-1234-5678", "cardBrand", "SHINHAN"));
        signup.put("bankAccount", Map.of("bankCode", "088", "accountNo", "110-123-456789", "accountHolder", name));
        signup.put("agreements", List.of(
                Map.of("termsId", freelancerTermsId, "agreed", true),
                Map.of("termsId", privacyTermsId, "agreed", true),
                Map.of("termsId", marketingTermsId, "agreed", true)));

        mockMvc.perform(post("/api/v1/auth/signup/freelancer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","role":"FREELANCER"}"""
                                .formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie accessToken = loginResult.getResponse().getCookie("accessToken");

        Map<String, Object> resumeBody = new LinkedHashMap<>();
        resumeBody.put("profileFileId", profileFileId);
        resumeBody.put("contactPhone", "");
        resumeBody.put("contactEmail", "");
        resumeBody.put("zipCode", "06234");
        resumeBody.put("address", "서울특별시 강남구 테헤란로 123");
        resumeBody.put("addressDetail", "2층");
        resumeBody.put("selfIntroduction", selfIntroduction);
        resumeBody.put("portfolioFileId", portfolioFileId);
        resumeBody.put("educations", List.of(Map.of(
                "startDate", "2014-03-01", "endDate", "2018-02-28",
                "schoolName", "페어링대학교", "major", "컴퓨터공학",
                "graduationStatus", "GRADUATED", "campusType", "MAIN")));
        resumeBody.put("careers", List.of(Map.of(
                "startDate", "2018-03-01", "endDate", "2023-02-28",
                "companyName", "주식회사 예시", "department", "서버개발팀", "position", "대리",
                "jobDescription", "백엔드 개발")));
        resumeBody.put("certificates", List.of());
        resumeBody.put("links", List.of());
        resumeBody.put("agreements", Map.of(
                "profileCollectionAgreed", true, "profileProvisionAgreed", true,
                "aiAnalysisAgreed", true, "careerPortfolioUsageAgreed", true));

        mockMvc.perform(put("/api/v1/freelancers/me/resume")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resumeBody)))
                .andExpect(status().isOk());

        // 후보 카드의 사진은 이력서가 아니라 freelancer_profile 의 것이다. 둘은 다른 칸이라
        // 이력서만 저장하면 카드 사진이 비어 있다.
        Map<String, Object> profileUpdate = new LinkedHashMap<>();
        profileUpdate.put("profileFileId", profileFileId);
        profileUpdate.put("phone", signup.get("phone"));
        profileUpdate.put("address", signup.get("address"));
        profileUpdate.put("aiMatchingAgreed", true);

        mockMvc.perform(patch("/api/v1/freelancers/me")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileUpdate)))
                .andExpect(status().isOk());

        Long accountId = accountRepository.findByEmailAndRoleAndDeletedAtIsNull(email,
                        com.pairing.account.domain.model.Role.FREELANCER)
                .orElseThrow().getId();
        Long freelancerProfileId = freelancerProfileRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .orElseThrow().getId();

        return new Freelancer(accountId, freelancerProfileId, accessToken, profileFileId);
    }

    @Test
    @DisplayName("getSummaries 는 여러 후보를 배치로 조회해도 각자의 이름·사진이 서로 섞이지 않는다")
    void getSummariesDoesNotMixDifferentCandidates() throws Exception {
        Freelancer a = signUpFreelancerWithResume("batch-a@pairing.com", "김에이", "1990-01-01", "A입니다");
        Freelancer b = signUpFreelancerWithResume("batch-b@pairing.com", "이비", "1992-02-02", "B입니다");

        Map<Long, FreelancerCandidateSummaryResult> summaries = freelancerCandidateSummaryUseCase
                .getSummaries(List.of(a.freelancerProfileId(), b.freelancerProfileId()));

        assertThat(summaries).hasSize(2);

        FreelancerCandidateSummaryResult resultA = summaries.get(a.freelancerProfileId());
        FreelancerCandidateSummaryResult resultB = summaries.get(b.freelancerProfileId());

        assertThat(resultA.name()).isEqualTo("김에이");
        assertThat(resultA.accountId()).isEqualTo(a.accountId());
        assertThat(resultA.profileImageUrl()).contains("batch-a@pairing.com");

        assertThat(resultB.name()).isEqualTo("이비");
        assertThat(resultB.accountId()).isEqualTo(b.accountId());
        assertThat(resultB.profileImageUrl()).contains("batch-b@pairing.com");
    }

    @Test
    @DisplayName("getSummaries 는 입력 순서를 유지한다 — 매칭 후보는 점수 내림차순으로 넘어온다")
    void getSummariesPreservesInputOrder() throws Exception {
        Freelancer a = signUpFreelancerWithResume("order-a@pairing.com", "순서A", "1990-01-01", "소개A");
        Freelancer b = signUpFreelancerWithResume("order-b@pairing.com", "순서B", "1991-01-01", "소개B");

        // b, a 순서로 넣으면 결과도 b, a 순서여야 한다.
        Map<Long, FreelancerCandidateSummaryResult> summaries = freelancerCandidateSummaryUseCase
                .getSummaries(List.of(b.freelancerProfileId(), a.freelancerProfileId()));

        assertThat(new ArrayList<>(summaries.keySet()))
                .containsExactly(b.freelancerProfileId(), a.freelancerProfileId());
    }

    @Test
    @DisplayName("getSummaries 는 없는 freelancerProfileId 를 예외 없이 결과에서 뺀다")
    void getSummariesSkipsUnknownId() throws Exception {
        Freelancer a = signUpFreelancerWithResume("known@pairing.com", "존재함", "1990-01-01", "소개");

        Map<Long, FreelancerCandidateSummaryResult> summaries = freelancerCandidateSummaryUseCase
                .getSummaries(List.of(a.freelancerProfileId(), 999_999L));

        assertThat(summaries).hasSize(1);
        assertThat(summaries).containsKey(a.freelancerProfileId());
    }

    @Test
    @DisplayName("findResumes 는 여러 계정을 배치로 조회해도 생년월일·사진이 서로 섞이지 않는다")
    void findResumesDoesNotMixDifferentAccounts() throws Exception {
        Freelancer a = signUpFreelancerWithResume("resume-a@pairing.com", "김레쥬메", "1985-05-05", "A 소개");
        Freelancer b = signUpFreelancerWithResume("resume-b@pairing.com", "이레쥬메", "1993-03-03", "B 소개");

        Map<Long, ResumeResult> resumes = resumeUseCase.findResumes(List.of(a.accountId(), b.accountId()));

        assertThat(resumes).hasSize(2);

        ResumeResult resultA = resumes.get(a.accountId());
        ResumeResult resultB = resumes.get(b.accountId());

        assertThat(resultA.name()).isEqualTo("김레쥬메");
        assertThat(resultA.birthDate()).isEqualTo(java.time.LocalDate.of(1985, 5, 5));
        assertThat(resultA.selfIntroduction()).isEqualTo("A 소개");
        assertThat(resultA.profileImageUrl()).contains("resume-a@pairing.com");

        assertThat(resultB.name()).isEqualTo("이레쥬메");
        assertThat(resultB.birthDate()).isEqualTo(java.time.LocalDate.of(1993, 3, 3));
        assertThat(resultB.selfIntroduction()).isEqualTo("B 소개");
        assertThat(resultB.profileImageUrl()).contains("resume-b@pairing.com");
    }

    @Test
    @DisplayName("findResumes 는 이력서를 등록하지 않은 계정을 결과에서 뺀다")
    void findResumesSkipsAccountWithoutResume() throws Exception {
        Freelancer a = signUpFreelancerWithResume("has-resume@pairing.com", "작성함", "1990-01-01", "소개");
        Long noResumeAccountId = accountQueryUseCase.findByEmailAndRole(
                        "has-resume@pairing.com", com.pairing.account.domain.model.Role.FREELANCER)
                .orElseThrow().getId() + 100_000L;

        Map<Long, ResumeResult> resumes = resumeUseCase.findResumes(List.of(a.accountId(), noResumeAccountId));

        assertThat(resumes).hasSize(1);
        assertThat(resumes).containsKey(a.accountId());
    }
}
