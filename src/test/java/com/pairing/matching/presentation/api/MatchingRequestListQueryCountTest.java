package com.pairing.matching.presentation.api;

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
import com.pairing.freelancer.application.command.UpsertConditionCommand;
import com.pairing.freelancer.application.usecase.FreelancerConditionUseCase;
import com.pairing.global.ratelimit.RateLimitProvider;
import com.pairing.matching.application.port.out.MatchingPort;
import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.matching.domain.model.MatchingSnapshot;
import com.pairing.matching.domain.model.SnapshotType;
import com.pairing.matching.domain.repository.MatchingRequestRepository;
import com.pairing.matching.domain.repository.MatchingSnapshotRepository;
import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.SkillLevel;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.terms.domain.model.TermsCode;
import com.pairing.terms.infrastructure.persistence.SpringDataTermsAgreementRepository;
import com.pairing.terms.infrastructure.persistence.SpringDataTermsRepository;
import com.pairing.terms.infrastructure.persistence.TermsJpaEntity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "매칭 요청 목록 N+1" 최적화의 개선 전/후 실측용. 실제 쿼리 수를 Hibernate Statistics로 센다
 * (k6로는 못 잰다 — 쿼리 수는 지연이 아니라 왕복 횟수라 부하 도구가 아니라 여기서 재야 한다).
 *
 * <p>프로젝트 2개(포지션 1개씩)에 걸쳐 매칭 요청 8건을 심고 {@code GET /requests}(size=20, 필터 없음
 * — "내가 보낸 요청 전체")를 한 번 호출했을 때 실행된 쿼리 수를 출력한다. 페이지 필터가 없으면
 * 두 프로젝트가 섞여 회사 프로필 캐시 이득이 가장 작게 나오는 조건이라, 여기서 줄어드는 수치는
 * "최소 보장치"에 가깝다(프로젝트 하나로 필터링하는 실사용 시나리오는 이보다 더 줄어든다).
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
class MatchingRequestListQueryCountTest {

    private static final String CLIENT_EMAIL = "query-count-client@pairing.com";
    private static final String FREELANCER_EMAIL = "query-count-freelancer@pairing.com";
    private static final String PASSWORD = "Passw0rd!";
    private static final Long PROJECT_A_ID = 7_101L;
    private static final Long POSITION_A_ID = 7_101L;
    private static final Long PROJECT_B_ID = 7_102L;
    private static final Long POSITION_B_ID = 7_102L;

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
    private MatchingRequestRepository matchingRequestRepository;
    @Autowired
    private MatchingSnapshotRepository matchingSnapshotRepository;
    @Autowired
    private FreelancerConditionUseCase freelancerConditionUseCase;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

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
    @MockitoBean
    private MatchingPort matchingPort;
    @MockitoBean
    private RateLimitProvider rateLimitProvider;

    private Long clientTermsId;
    private Long freelancerTermsId;
    private Long privacyTermsId;
    private Long marketingTermsId;
    private Cookie clientAccessToken;
    private Long clientAccountId;
    private Long freelancerAccountId;
    private Long freelancerProfileId;

    @BeforeEach
    void setUp() throws Exception {
        matchingRequestRepository.findExpiredPending(LocalDateTime.now().plusYears(10))
                .forEach(r -> jdbcTemplate.update("DELETE FROM matching_request WHERE id = ?", r.getId()));
        jdbcTemplate.update("DELETE FROM matching_snapshot WHERE project_id IN (?, ?)", PROJECT_A_ID, PROJECT_B_ID);
        jdbcTemplate.update("DELETE FROM project_position WHERE id IN (?, ?)", POSITION_A_ID, POSITION_B_ID);
        jdbcTemplate.update("DELETE FROM project WHERE id IN (?, ?)", PROJECT_A_ID, PROJECT_B_ID);
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
        signUpFreelancer();

        Long clientProfileId = clientProfileRepository.findByAccountIdAndDeletedAtIsNull(clientAccountId)
                .orElseThrow().getId();
        seedProjectWithPosition(PROJECT_A_ID, POSITION_A_ID, clientProfileId);
        seedProjectWithPosition(PROJECT_B_ID, POSITION_B_ID, clientProfileId);
        seedSnapshots(PROJECT_A_ID, POSITION_A_ID, "A");
        seedSnapshots(PROJECT_B_ID, POSITION_B_ID, "B");

        // 8건: 포지션 A에 4건, 포지션 B에 4건. 같은 프리랜서를 반복 사용한다 — 지금 코드는 값을
        // 캐시하지 않으므로 반복이어도 행마다 다시 부른다(이 자체가 최적화 대상이다).
        for (int i = 0; i < 4; i++) {
            matchingRequestRepository.save(MatchingRequest.create(PROJECT_A_ID, POSITION_A_ID,
                    7_101_000L + i, freelancerProfileId));
        }
        for (int i = 0; i < 4; i++) {
            matchingRequestRepository.save(MatchingRequest.create(PROJECT_B_ID, POSITION_B_ID,
                    7_102_000L + i, freelancerProfileId));
        }
    }

    private Long saveTerms(TermsCode code, String title, boolean required, String targetRole) {
        return termsRepository.save(new TermsJpaEntity(
                null, code, code.getType(), "v1.0", title, "본문", required, targetRole,
                LocalDateTime.now().minusDays(1)
        )).getId();
    }

    private void seedProjectWithPosition(Long projectId, Long positionId, Long clientProfileId) {
        jdbcTemplate.update(
                "INSERT INTO project (id, client_id, title, start_desired_date, start_negotiable, "
                        + "period_value, period_unit, budget_amount, work_style, work_form, work_location, "
                        + "current_situation, main_task, detail_scope, extra_note, "
                        + "status, payment_status, total_headcount, confirmed_headcount, "
                        + "extension_count, free_rerecommend_used, paid_rerecommend_used) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                projectId, clientProfileId, "쿼리 수 측정용 프로젝트 " + projectId, LocalDate.now().plusDays(14), false,
                6, "MONTH", 60_000_000L, "ONSITE", "FULL_TIME", "서울 강남구 테헤란로",
                "현행 시스템 운영중", "백엔드 API 개발", "주문/결제 도메인 개발", "MSA 경험자 우대",
                "RECRUITING", "SUCCESS_FEE_PAID", 2, 0, 0, 0, 0);

        jdbcTemplate.update(
                "INSERT INTO project_position (id, project_id, position_no, job_category, job_role, "
                        + "min_career_years, headcount, confirmed_count, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                positionId, projectId, 1, "DEVELOPMENT", "BACKEND", 3, 2, 0, "RECRUITING");
    }

    private void seedSnapshots(Long projectId, Long positionId, String label) throws Exception {
        Map<String, Object> projectPayload = new LinkedHashMap<>();
        projectPayload.put("title", "쿼리 수 측정용 프로젝트 " + label);
        projectPayload.put("companyName", "주식회사 페어링테크");
        projectPayload.put("workLabel", "상주 · 풀타임");
        projectPayload.put("periodLabel", "6개월");
        projectPayload.put("startDesiredDate", LocalDate.now().plusDays(14));
        projectPayload.put("budgetAmount", 60_000_000L);
        projectPayload.put("mainTask", "주문 시스템 API 개발");
        projectPayload.put("currentSituation", "진행 상황 " + label);
        projectPayload.put("startNegotiable", true);
        projectPayload.put("periodValue", 6);
        projectPayload.put("periodUnit", "MONTH");
        projectPayload.put("detailScope", "세부 업무 범위 " + label);
        projectPayload.put("extraNote", "우대사항 " + label);
        projectPayload.put("workLocation", "근무 장소 " + label);
        matchingSnapshotRepository.save(MatchingSnapshot.create(projectId, positionId, null,
                SnapshotType.PROJECT, objectMapper.writeValueAsString(projectPayload)));

        Map<String, Object> positionPayload = new LinkedHashMap<>();
        positionPayload.put("jobRole", JobRole.BACKEND);
        positionPayload.put("requiredSkills", List.of(SkillCode.SPRING_BOOT));
        positionPayload.put("minCareerYears", 3);
        positionPayload.put("headcount", 2);
        positionPayload.put("totalHeadcount", 2);
        matchingSnapshotRepository.save(MatchingSnapshot.create(projectId, positionId, null,
                SnapshotType.POSITION, objectMapper.writeValueAsString(positionPayload)));
    }

    private void signUpAndLoginClient() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("companyName", "주식회사 페어링테크");
        body.put("businessNo", "1234567890");
        body.put("businessField", "IT_CONTENTS_AI");
        body.put("employeeCount", "SIZE_10_49");
        body.put("address", Map.of("sido", "서울", "sigungu", "강남구", "roadAddress", "서울 강남구 테헤란로 1",
                "addressDetail", "10층", "zipCode", "06234"));
        body.put("email", CLIENT_EMAIL);
        body.put("name", "김클라");
        body.put("phone", "010-1111-2222");
        body.put("password", PASSWORD);
        body.put("passwordConfirm", PASSWORD);
        body.put("card", Map.of("cardNumber", "1234-5678-1234-5678", "cardBrand", "SHINHAN"));
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

    private void signUpFreelancer() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "이프리");
        body.put("phone", "010-3333-4444");
        body.put("email", FREELANCER_EMAIL);
        body.put("password", PASSWORD);
        body.put("passwordConfirm", PASSWORD);
        body.put("birthDate", "1995-03-01");
        body.put("address", Map.of("sido", "서울", "sigungu", "강남구", "roadAddress", "서울 강남구 테헤란로 1",
                "addressDetail", "10층", "zipCode", "06234"));
        body.put("card", Map.of("cardNumber", "1234-5678-1234-5678", "cardBrand", "SHINHAN"));
        body.put("bankAccount", Map.of("bankCode", "088", "accountNo", "110-123-456789", "accountHolder", "이프리"));
        body.put("agreements", List.of(
                Map.of("termsId", freelancerTermsId, "agreed", true),
                Map.of("termsId", privacyTermsId, "agreed", true),
                Map.of("termsId", marketingTermsId, "agreed", true)));

        mockMvc.perform(post("/api/v1/auth/signup/freelancer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        freelancerAccountId = accountRepository.findByEmailAndRoleAndDeletedAtIsNull(FREELANCER_EMAIL,
                com.pairing.account.domain.model.Role.FREELANCER).orElseThrow().getId();
        freelancerProfileId = jdbcTemplate.queryForObject(
                "SELECT id FROM freelancer_profile WHERE account_id = ?", Long.class, freelancerAccountId);

        freelancerConditionUseCase.upsert(new UpsertConditionCommand(
                freelancerAccountId, JobCategory.DEVELOPMENT, JobRole.BACKEND,
                WorkStyle.REMOTE, WorkForm.FULL_TIME, PayUnit.MONTHLY, 6_500_000L, 5_500_000L,
                LocalDate.now().plusDays(14), false, 6, PeriodUnit.MONTH, true, 5,
                List.of(new UpsertConditionCommand.Skill(SkillCode.JAVA, SkillLevel.ADVANCED),
                        new UpsertConditionCommand.Skill(SkillCode.SPRING_BOOT, SkillLevel.ADVANCED))));
    }

    @Test
    void printsQueryCountForSentRequestsList() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/v1/matchings/requests")
                        .param("size", "20")
                        .cookie(clientAccessToken))
                .andExpect(status().isOk());

        long queryCount = statistics.getPrepareStatementCount();
        System.out.println("=====QUERY_COUNT===== GET /requests (8 rows, 2 positions) -> "
                + queryCount + " prepared statements =====QUERY_COUNT=====");
    }
}
