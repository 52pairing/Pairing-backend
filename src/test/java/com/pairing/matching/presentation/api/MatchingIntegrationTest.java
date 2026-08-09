package com.pairing.matching.presentation.api;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.pairing.global.ratelimit.RateLimitPolicy;
import com.pairing.global.ratelimit.RateLimitProvider;
import com.pairing.matching.application.port.out.MatchingPort;
import com.pairing.matching.application.result.MatchingRecommendation;
import com.pairing.matching.application.result.RankedFreelancer;
import com.pairing.matching.domain.model.MatchingCandidate;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.MatchingSnapshot;
import com.pairing.matching.domain.model.RecommendationType;
import com.pairing.matching.domain.model.SnapshotType;
import com.pairing.matching.domain.repository.MatchingCandidateRepository;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import com.pairing.matching.domain.repository.MatchingSnapshotRepository;
import com.pairing.matching.infrastructure.persistence.SpringDataMatchingCandidateRepository;
import com.pairing.matching.infrastructure.persistence.SpringDataMatchingRequestRepository;
import com.pairing.matching.infrastructure.persistence.SpringDataMatchingRoundRepository;
import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.SkillLevel;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import com.pairing.project.domain.model.ProjectStatus;
import com.pairing.terms.domain.model.TermsCode;
import com.pairing.terms.infrastructure.persistence.SpringDataTermsAgreementRepository;
import com.pairing.terms.infrastructure.persistence.SpringDataTermsRepository;
import com.pairing.terms.infrastructure.persistence.TermsJpaEntity;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 후보 조회 -&gt; 요청 발송/조회 -&gt; 수락(협상 생성) -&gt; 재추천까지, 다른 도메인 어댑터가 실제로
 * 붙은 상태에서 실제 요청으로 확인한다.
 *
 * <p>ProjectDirectoryPort/NegotiationPort/FreelancerDirectoryPort 전부 실제 도메인을 그대로 타므로
 * 이 테스트가 통과하면 어댑터 교체가 실제로 맞물려 동작한다는 뜻이다(seedFreelancerProfile 참고).
 */
@SpringBootTest
@AutoConfigureMockMvc
class MatchingIntegrationTest {

    private static final String CLIENT_EMAIL = "matching-client@pairing.com";
    private static final String FREELANCER_EMAIL = "matching-freelancer@pairing.com";
    private static final String PASSWORD = "Passw0rd!";
    private static final Long PROJECT_ID = 7001L;
    private static final Long POSITION_ID = 7001L;

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
    private SpringDataMatchingRoundRepository matchingRoundJpaRepository;
    @Autowired
    private SpringDataMatchingCandidateRepository matchingCandidateJpaRepository;
    @Autowired
    private SpringDataMatchingRequestRepository matchingRequestJpaRepository;
    @Autowired
    private MatchingRoundRepository matchingRoundRepository;
    @Autowired
    private MatchingCandidateRepository matchingCandidateRepository;
    @Autowired
    private MatchingSnapshotRepository matchingSnapshotRepository;
    @Autowired
    private ProjectQueryUseCase projectQueryUseCase;
    @Autowired
    private FreelancerConditionUseCase freelancerConditionUseCase;
    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    private Cookie freelancerAccessToken;
    private Long clientAccountId;
    private Long freelancerAccountId;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM negotiation_message WHERE negotiation_id IN "
                + "(SELECT id FROM negotiation WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM negotiation_condition WHERE negotiation_id IN "
                + "(SELECT id FROM negotiation WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM negotiation WHERE project_id = ?", PROJECT_ID);
        matchingRequestJpaRepository.deleteAll();
        matchingCandidateJpaRepository.deleteAll();
        matchingRoundJpaRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM matching_snapshot WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM position_skill WHERE position_id = ?", POSITION_ID);
        jdbcTemplate.update("DELETE FROM project_position WHERE id = ?", POSITION_ID);
        jdbcTemplate.update("DELETE FROM project WHERE id = ?", PROJECT_ID);
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
        // 재추천 엔드포인트의 레이트리밋은 Redis 기반(RateLimitProvider -> lettuceProxyManager)이라
        // Redis가 없는 CI/H2 테스트 환경에서는 실제로 호출하면 연결 실패로 500이 난다. 로컬은 Redis가
        // 떠 있어서 우연히 통과했었다(CI에서 처음 발견). 여기서는 항상 허용하는 로컬 버킷으로 대체한다.
        given(rateLimitProvider.getBucket(any(RateLimitPolicy.class), anyString()))
                .willReturn(Bucket.builder().addLimit(Bandwidth.simple(1000, Duration.ofMinutes(1))).build());

        signUpAndLoginClient();
        signUpAndLoginFreelancer();
        seedFreelancerProfile();

        Long clientProfileId = clientProfileRepository.findByAccountIdAndDeletedAtIsNull(clientAccountId)
                .orElseThrow().getId();
        seedProjectWithPosition(clientProfileId);
        seedMatchingSnapshots();
    }

    private Long saveTerms(TermsCode code, String title, boolean required, String targetRole) {
        return termsRepository.save(new TermsJpaEntity(
                null, code, code.getType(), "v1.0", title, "본문", required, targetRole,
                java.time.LocalDateTime.now().minusDays(1)
        )).getId();
    }

    /**
     * 회원가입이 만든 freelancer_profile(자동 채번 id)을 지우고, id를 freelancerAccountId와 같게
     * 다시 심는다. matching의 FreelancerDirectoryPort.resolveFreelancerId는 accountId로
     * freelancer_profile을 찾아 그 id를 돌려주므로, id를 accountId와 같게 맞춰두면 후보 카드
     * 조회(findCardSummary)와 수락 권한 체크(resolveFreelancerId)가 항상 같은 사람을 가리킨다.
     */
    private void seedFreelancerProfile() {
        jdbcTemplate.update("DELETE FROM freelancer_profile WHERE account_id = ?", freelancerAccountId);
        jdbcTemplate.update(
                "INSERT INTO freelancer_profile (id, account_id, birth_date, ai_matching_agreed, grade) "
                        + "VALUES (?, ?, ?, ?, ?)",
                freelancerAccountId, freelancerAccountId, LocalDate.of(1995, 3, 1), true, "SENIOR");
        // id를 명시적으로 채워 넣으면 H2 identity 채번 카운터가 이 값을 모른 채로 남는다. 다른 테스트
        // 클래스가 같은(캐시된) 컨텍스트에서 freelancer_profile을 자동 채번으로 insert할 때 이 값과
        // 충돌할 수 있어(PRIMARY KEY violation), 카운터를 명시적으로 앞으로 당겨둔다.
        jdbcTemplate.execute("ALTER TABLE freelancer_profile ALTER COLUMN id RESTART WITH "
                + (freelancerAccountId + 1));

        // FreelancerDirectoryAdapter.findCondition이 실구현으로 바뀌면서 freelancer_condition이
        // 실제로 있어야 accept()가 성공한다. work_style은 의도적으로 REMOTE로 둔다 — 프로젝트가
        // ONSITE라(seedProjectWithPosition) 수락 시 조건이 갈려서 협상이 "조건 불일치"로 정상
        // 생성된다(조건이 하나도 안 갈리면 즉시 타결 경로로 빠져 이 테스트 범위를 벗어난다).
        freelancerConditionUseCase.upsert(new UpsertConditionCommand(
                freelancerAccountId, JobCategory.DEVELOPMENT, JobRole.BACKEND, null,
                WorkStyle.REMOTE, WorkForm.FULL_TIME, PayUnit.MONTHLY, 6_500_000L, 5_500_000L,
                LocalDate.now().plusDays(14), false, 6, PeriodUnit.MONTH, true, 5,
                List.of(new UpsertConditionCommand.Skill(SkillCode.JAVA, SkillLevel.ADVANCED),
                        new UpsertConditionCommand.Skill(SkillCode.SPRING_BOOT, SkillLevel.ADVANCED))));
    }

    /**
     * project + project_position + position_skill을 직접 심는다. project 도메인이 등록 API로
     * 만드는 것과 같은 모양이지만, 이 테스트는 project 자체가 아니라 matching이 그 데이터를
     * 제대로 읽어오는지가 목적이라 JDBC로 직접 채운다(ReviewIntegrationTest와 동일 방식).
     *
     * <p>work_style을 ONSITE로 둔 건 의도적이다 — seedFreelancerProfile이 심는 조건이 REMOTE라,
     * 수락 시 두 조건이 갈려야 협상이 "조건 불일치"로 정상 생성된다(조건이 하나도 안 갈리면 즉시
     * 타결 + 채팅방 생성 경로로 빠져 이 테스트 범위를 벗어난다).
     */
    private void seedProjectWithPosition(Long clientProfileId) {
        jdbcTemplate.update(
                "INSERT INTO project (id, client_id, title, start_desired_date, start_negotiable, "
                        + "period_value, period_unit, budget_amount, work_style, work_form, current_situation, "
                        + "main_task, status, payment_status, total_headcount, confirmed_headcount, "
                        + "extension_count, free_rerecommend_used, paid_rerecommend_used) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                PROJECT_ID, clientProfileId, "AI 추천 시스템 구축", LocalDate.now().plusDays(14), false,
                6, "MONTH", 60_000_000L, "ONSITE", "FULL_TIME", "현행 시스템 운영중", "백엔드 API 개발",
                "RECRUITING", "SUCCESS_FEE_PAID", 2, 0, 0, 0, 0);

        jdbcTemplate.update(
                "INSERT INTO project_position (id, project_id, position_no, job_category, job_role, "
                        + "min_career_years, headcount, confirmed_count, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                POSITION_ID, PROJECT_ID, 1, "DEVELOPMENT", "BACKEND", 3, 2, 0, "RECRUITING");

        jdbcTemplate.update(
                "INSERT INTO position_skill (position_id, skill_code) VALUES (?, ?)",
                POSITION_ID, "SPRING_BOOT");
    }

    /**
     * 매칭 요청 카드는 라이브 조회가 아니라 모집 시작 시점에 얼린 스냅샷을 읽는다(R32, 프로젝트
     * 수정 API 대비). 이 테스트는 project 도메인의 결제완료 이벤트 흐름(RecruitingStartedEvent)을
     * 안 타고 라운드를 직접 심기 때문에(seedRound), 실제 스냅샷 동결(RecruitingStartedPositionHandler.
     * freezeSnapshot)과 같은 모양으로 직접 심어준다.
     */
    private void seedMatchingSnapshots() throws Exception {
        Map<String, Object> projectPayload = new LinkedHashMap<>();
        projectPayload.put("title", "AI 추천 시스템 구축");
        projectPayload.put("companyName", "주식회사 페어링테크");
        projectPayload.put("workLabel", "상주 · 풀타임");
        projectPayload.put("periodLabel", "6개월");
        projectPayload.put("startDesiredDate", LocalDate.now().plusDays(14));
        projectPayload.put("budgetAmount", 60_000_000L);
        matchingSnapshotRepository.save(MatchingSnapshot.create(PROJECT_ID, POSITION_ID, null,
                SnapshotType.PROJECT, objectMapper.writeValueAsString(projectPayload)));

        Map<String, Object> positionPayload = new LinkedHashMap<>();
        positionPayload.put("jobRole", JobRole.BACKEND);
        positionPayload.put("requiredSkills", List.of(SkillCode.SPRING_BOOT));
        positionPayload.put("minCareerYears", 3);
        positionPayload.put("headcount", 2);
        positionPayload.put("totalHeadcount", 2);
        matchingSnapshotRepository.save(MatchingSnapshot.create(PROJECT_ID, POSITION_ID, null,
                SnapshotType.POSITION, objectMapper.writeValueAsString(positionPayload)));
    }

    private void signUpAndLoginClient() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("companyName", "주식회사 페어링테크");
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

    private MatchingRound seedRound(int recruitCount) {
        MatchingRound round = MatchingRound.create(PROJECT_ID, POSITION_ID, 1, RecommendationType.INITIAL,
                null, 0L, recruitCount, recruitCount * 3);
        round.complete();
        return matchingRoundRepository.save(round);
    }

    private MatchingCandidate seedExposedCandidate(Long roundId, int rank) {
        MatchingCandidate candidate = MatchingCandidate.createFromEmbedding(roundId, POSITION_ID,
                freelancerAccountId, 0.8);
        candidate.applyLlmResult(88.0, "요구 스킬 3개 중 3개 일치|경력 조건 충족");
        candidate.applyGradeWeight(0.0);
        candidate.applyGuard(true, null);
        candidate.expose(rank);
        return matchingCandidateRepository.save(candidate);
    }

    @Test
    @DisplayName("후보 조회는 실제 프리랜서 카드/조건으로 조립된다")
    void candidateListReflectsRealFreelancerData() throws Exception {
        MatchingRound round = seedRound(2);
        seedExposedCandidate(round.getId(), 1);

        mockMvc.perform(get("/api/v1/matchings/positions/" + POSITION_ID + "/candidates")
                        .cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates.length()").value(1))
                .andExpect(jsonPath("$.data.candidates[0].name").value("이프리"))
                .andExpect(jsonPath("$.data.candidates[0].grade").value("SENIOR"))
                .andExpect(jsonPath("$.data.candidates[0].skills[0]").value("JAVA"))
                .andExpect(jsonPath("$.data.candidates[0].requested").value(false))
                .andExpect(jsonPath("$.data.candidates[0].rejected").value(false));
    }

    @Test
    @DisplayName("후보를 거절하면 목록에서 비활성으로 표시된다")
    void rejectCandidateMarksItInactive() throws Exception {
        MatchingRound round = seedRound(2);
        MatchingCandidate candidate = seedExposedCandidate(round.getId(), 1);

        mockMvc.perform(post("/api/v1/matchings/candidates/" + candidate.getId() + "/rejection")
                        .cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates[0].rejected").value(true));
    }

    @Test
    @DisplayName("매칭 요청을 보내면 실제 프로젝트/회사 정보로 카드가 채워지고 양쪽 목록에 나타난다")
    void sendRequestPopulatesRealProjectAndShowsInBothLists() throws Exception {
        MatchingRound round = seedRound(2);
        MatchingCandidate candidate = seedExposedCandidate(round.getId(), 1);

        Map<String, Object> body = Map.of("positionId", POSITION_ID, "candidateIds", List.of(candidate.getId()));
        mockMvc.perform(post("/api/v1/matchings/requests")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].projectTitle").value("AI 추천 시스템 구축"))
                .andExpect(jsonPath("$.data[0].companyName").value("주식회사 페어링테크"))
                .andExpect(jsonPath("$.data[0].workLabel").value("상주 · 풀타임"))
                .andExpect(jsonPath("$.data[0].counterpartName").value("이프리"));

        mockMvc.perform(get("/api/v1/matchings/requests")
                        .param("projectId", String.valueOf(PROJECT_ID))
                        .cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));

        mockMvc.perform(get("/api/v1/matchings/requests/received")
                        .cookie(freelancerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].counterpartName").value("주식회사 페어링테크"));
    }

    private Long sendRequestAndGetId(Long candidateId) throws Exception {
        Map<String, Object> body = Map.of("positionId", POSITION_ID, "candidateIds", List.of(candidateId));
        MvcResult result = mockMvc.perform(post("/api/v1/matchings/requests")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get("data").get(0).get("requestId").asLong();
    }

    @Test
    @DisplayName("매칭 요청을 수락하면 실제 협상이 생성되고 상태가 협상중으로 바뀐다")
    void acceptRequestCreatesRealNegotiation() throws Exception {
        MatchingRound round = seedRound(2);
        MatchingCandidate candidate = seedExposedCandidate(round.getId(), 1);
        Long requestId = sendRequestAndGetId(candidate.getId());

        mockMvc.perform(post("/api/v1/matchings/requests/" + requestId + "/acceptance")
                        .cookie(freelancerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEGOTIATING"))
                .andExpect(jsonPath("$.data.negotiationId").isNotEmpty())
                .andExpect(jsonPath("$.data.currentRound").isNotEmpty());

        assertThat(projectQueryUseCase.findStatus(PROJECT_ID)).isEqualTo(ProjectStatus.NEGOTIATING);
    }

    @Test
    @DisplayName("프리랜서가 매칭 요청을 거절하면 상태가 거절로 바뀐다")
    void rejectRequestMarksRejected() throws Exception {
        MatchingRound round = seedRound(2);
        MatchingCandidate candidate = seedExposedCandidate(round.getId(), 1);
        Long requestId = sendRequestAndGetId(candidate.getId());

        mockMvc.perform(post("/api/v1/matchings/requests/" + requestId + "/rejection")
                        .cookie(freelancerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"일정이 맞지 않습니다."}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    @DisplayName("재추천을 요청하면 AI 서버(Pairing-python) 응답으로 새 회차/후보가 만들어진다")
    void rerecommendCreatesNewRoundFromAiServerResponse() throws Exception {
        seedRound(2);
        given(matchingPort.recommend(eq(POSITION_ID), eq(2), eq(3)))
                .willReturn(new MatchingRecommendation(POSITION_ID, "gemini-2.0-flash",
                        List.of(new RankedFreelancer(freelancerAccountId, 91.0,
                                "요구 스킬 3개 중 3개 일치|경력 조건 충족"))));

        mockMvc.perform(post("/api/v1/matchings/positions/" + POSITION_ID + "/rerecommendations")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PAID","quantity":2}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.candidates.length()").value(1))
                .andExpect(jsonPath("$.data.candidates[0].name").value("이프리"));
    }

    @Test
    @DisplayName("유료 재추천인데 quantity가 없으면 500이 아니라 400으로 응답한다")
    void rerecommendPaidWithoutQuantityReturnsBadRequest() throws Exception {
        seedRound(2);

        mockMvc.perform(post("/api/v1/matchings/positions/" + POSITION_ID + "/rerecommendations")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PAID"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MT_012"));
    }

    @Test
    @DisplayName("재추천 종류로 INITIAL을 보내면 400으로 거부한다")
    void rerecommendWithInitialTypeReturnsBadRequest() throws Exception {
        seedRound(2);

        mockMvc.perform(post("/api/v1/matchings/positions/" + POSITION_ID + "/rerecommendations")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"INITIAL","quantity":3}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MT_013"));
    }

    @Test
    @DisplayName("모집이 종료된 프로젝트면 매칭 요청 발송을 막는다")
    void sendRequestsBlockedWhenProjectClosed() throws Exception {
        MatchingRound round = seedRound(2);
        MatchingCandidate candidate = seedExposedCandidate(round.getId(), 1);
        jdbcTemplate.update("UPDATE project SET status = ? WHERE id = ?", "CLOSED", PROJECT_ID);

        Map<String, Object> body = Map.of("positionId", POSITION_ID, "candidateIds", List.of(candidate.getId()));
        mockMvc.perform(post("/api/v1/matchings/requests")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MT_014"));
    }

    @Test
    @DisplayName("취소된 프로젝트면 재추천을 막는다")
    void rerecommendBlockedWhenProjectCanceled() throws Exception {
        seedRound(2);
        jdbcTemplate.update("UPDATE project SET status = ? WHERE id = ?", "CANCELED", PROJECT_ID);

        mockMvc.perform(post("/api/v1/matchings/positions/" + POSITION_ID + "/rerecommendations")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PAID","quantity":2}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MT_014"));
    }
}
