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
import com.pairing.matching.application.usecase.MatchingRequestCommandUseCase;
import com.pairing.matching.domain.model.MatchingCandidate;
import com.pairing.matching.application.service.StaleRoundRecoveryService;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.MatchingRoundStatus;
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
import com.pairing.global.config.SyncTaskExecutorTestConfig;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
@Import(SyncTaskExecutorTestConfig.class)
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
    @Autowired
    private MatchingRequestCommandUseCase matchingRequestCommandUseCase;
    @Autowired
    private StaleRoundRecoveryService staleRoundRecoveryService;

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
        projectPayload.put("mainTask", "주문 시스템 API 개발");
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
        body.put("address", "서울 강남구 테헤란로 1");
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
        return seedExposedCandidate(roundId, rank, null);
    }

    /** 회차 번호를 지정해 회차를 만든다. 재추천으로 회차가 쌓이는 상황을 재현할 때 쓴다. */
    private MatchingRound seedRound(int recruitCount, int roundNo, RecommendationType type, Integer requestedCount) {
        MatchingRound round = MatchingRound.create(PROJECT_ID, POSITION_ID, roundNo, type,
                requestedCount, 0L, recruitCount, recruitCount * 3);
        round.complete();
        return matchingRoundRepository.save(round);
    }

    /** {@link #seedExposedCandidate(Long, int)}와 같되 프리랜서를 지정한다(회차별로 다른 사람이어야 한다). */
    private MatchingCandidate seedExposedCandidateFor(Long roundId, int rank, Long freelancerId) {
        MatchingCandidate candidate = MatchingCandidate.createFromEmbedding(roundId, POSITION_ID,
                freelancerId, 0.8);
        candidate.applyLlmResult(88.0, "요구 스킬 3개 중 3개 일치|경력 조건 충족");
        candidate.applyGradeWeight(0.0);
        candidate.applyGuard(true, null);
        candidate.expose(rank);
        return matchingCandidateRepository.save(candidate);
    }

    @Test
    @DisplayName("추천 라운드가 아직 없으면 에러가 아니라 준비중으로 내려간다")
    void candidateListIsPreparingWhileTheFirstRoundIsStillBeingBuilt() throws Exception {
        // 최초 추천은 착수금 결제 이벤트를 받아 비동기로 돌고 LLM 호출까지 수 초~수십 초가 걸린다.
        // 결제 직후 추천 후보 탭을 열면 라운드가 없는 게 정상인데, 예전엔 MT_001 을 404로 던져서
        // 화면에 빨간 에러가 뜨고 "다시 시도"를 눌러야 후보가 보였다.
        mockMvc.perform(get("/api/v1/matchings/positions/" + POSITION_ID + "/candidates")
                        .cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preparing").value(true))
                .andExpect(jsonPath("$.data.candidates").isEmpty())
                .andExpect(jsonPath("$.data.roundId").doesNotExist())
                // 대기 중에도 "0/4명" 같은 표기를 그릴 수 있어야 한다.
                .andExpect(jsonPath("$.data.headcount").value(2));

        // 라운드가 생기면 preparing 이 꺼지고 평소대로 내려간다.
        MatchingRound round = seedRound(2);
        seedExposedCandidate(round.getId(), 1);

        mockMvc.perform(get("/api/v1/matchings/positions/" + POSITION_ID + "/candidates")
                        .cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preparing").value(false))
                .andExpect(jsonPath("$.data.failed").value(false))
                .andExpect(jsonPath("$.data.candidates.length()").value(1));
    }

    @Test
    @DisplayName("회차는 생겼지만 아직 채우는 중이면 후보 0명이 아니라 준비중이다")
    void runningRoundIsPreparingNotEmpty() throws Exception {
        // 회차 레코드는 AI 호출 **전에** 커밋된다(2026-08-13). 그래서 회차가 있다고 후보가 있는 게
        // 아니다. 상태를 안 보면 채우는 중인 회차가 "후보 없음"으로 보인다.
        MatchingRound running = MatchingRound.create(PROJECT_ID, POSITION_ID, 1,
                RecommendationType.INITIAL, null, 0L, 2, 6);
        matchingRoundRepository.save(running);

        mockMvc.perform(get("/api/v1/matchings/positions/" + POSITION_ID + "/candidates")
                        .cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preparing").value(true))
                .andExpect(jsonPath("$.data.failed").value(false))
                .andExpect(jsonPath("$.data.candidates").isEmpty());
    }

    /** 회차를 만든 뒤 생성 시각을 과거로 돌린다. created_at 은 DB 기본값이라 직접 못 넣는다. */
    private MatchingRound seedRunningRoundCreatedMinutesAgo(int minutesAgo) {
        MatchingRound running = matchingRoundRepository.save(MatchingRound.create(PROJECT_ID, POSITION_ID, 1,
                RecommendationType.INITIAL, null, 0L, 2, 6));
        jdbcTemplate.update("UPDATE matching_round SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusMinutes(minutesAgo)), running.getId());
        return running;
    }

    @Test
    @DisplayName("AI 호출 중 컨테이너가 죽어 RUNNING 으로 멈춘 회차를 스케줄러가 되살린다")
    void staleRunningRoundIsRefilled() throws Exception {
        // 후보 저장과 회차 완료가 한 트랜잭션이라, 중간에 죽은 회차에는 후보가 하나도 없다.
        // 그래서 다시 채워도 중복이 생기지 않는다.
        MatchingRound stale = seedRunningRoundCreatedMinutesAgo(30);
        given(matchingPort.recommend(eq(POSITION_ID), eq(2), eq(3), eq(List.of()), anyLong())).willReturn(
                new MatchingRecommendation(POSITION_ID, "gemini-2.0-flash", List.of(
                        new RankedFreelancer(freelancerAccountId, 91.0, "요구 스킬 일치", 0.8125))));

        staleRoundRecoveryService.recoverStaleRounds();

        assertThat(matchingRoundRepository.findById(stale.getId()).orElseThrow().getStatus())
                .isEqualTo(MatchingRoundStatus.COMPLETED);
        mockMvc.perform(get("/api/v1/matchings/positions/" + POSITION_ID + "/candidates")
                        .cookie(clientAccessToken))
                .andExpect(jsonPath("$.data.preparing").value(false))
                .andExpect(jsonPath("$.data.candidates.length()").value(1));
    }

    @Test
    @DisplayName("되살리기도 실패하면 FAILED 로 닫는다 — RUNNING 으로 두면 매 주기마다 AI를 다시 부른다")
    void staleRoundIsClosedWhenRefillAlsoFails() throws Exception {
        MatchingRound stale = seedRunningRoundCreatedMinutesAgo(30);
        given(matchingPort.recommend(eq(POSITION_ID), eq(2), eq(3), eq(List.of()), anyLong()))
                .willThrow(new IllegalStateException("AI 서버 호출 실패"));

        staleRoundRecoveryService.recoverStaleRounds();

        assertThat(matchingRoundRepository.findById(stale.getId()).orElseThrow().getStatus())
                .isEqualTo(MatchingRoundStatus.FAILED);
    }

    @Test
    @DisplayName("이미 끝난 회차는 다시 채우지 않는다 — 인스턴스가 둘이면 같은 회차를 집을 수 있다")
    void alreadyFinishedRoundIsNotRefilled() throws Exception {
        // 서버 인스턴스가 둘 이상이면 각자의 스케줄러가 같은 회차를 집을 수 있다. 그대로 두면
        // Gemini 를 두 번 부르고 후보가 중복 저장된다.
        MatchingRound stale = seedRunningRoundCreatedMinutesAgo(30);
        given(matchingPort.recommend(eq(POSITION_ID), eq(2), eq(3), eq(List.of()), anyLong())).willReturn(
                new MatchingRecommendation(POSITION_ID, "gemini-2.0-flash", List.of(
                        new RankedFreelancer(freelancerAccountId, 91.0, "요구 스킬 일치", 0.8125))));

        staleRoundRecoveryService.recoverStaleRounds();
        // 첫 번째로 이미 COMPLETED 가 됐다. 두 번째 주기(또는 다른 인스턴스)가 또 돌아도
        // 후보가 늘어나면 안 된다.
        long candidateCountAfterFirst = matchingCandidateJpaRepository.count();
        staleRoundRecoveryService.recoverStaleRounds();

        assertThat(matchingCandidateJpaRepository.count()).isEqualTo(candidateCountAfterFirst);
        assertThat(matchingRoundRepository.findById(stale.getId()).orElseThrow().getStatus())
                .isEqualTo(MatchingRoundStatus.COMPLETED);
    }

    @Test
    @DisplayName("아직 진행 중일 수 있는 회차는 건드리지 않는다 — 다시 부르면 AI 비용이 두 배다")
    void freshRunningRoundIsNotTouched() throws Exception {
        MatchingRound fresh = seedRunningRoundCreatedMinutesAgo(1);

        staleRoundRecoveryService.recoverStaleRounds();

        assertThat(matchingRoundRepository.findById(fresh.getId()).orElseThrow().getStatus())
                .isEqualTo(MatchingRoundStatus.RUNNING);
        verify(matchingPort, never()).recommend(anyLong(), anyInt(), anyInt(), anyList(), anyLong());
    }

    @Test
    @DisplayName("추천 생성이 실패한 회차는 후보 없음이 아니라 실패로 내려간다")
    void failedRoundIsReportedAsFailed() throws Exception {
        MatchingRound failed = MatchingRound.create(PROJECT_ID, POSITION_ID, 1,
                RecommendationType.INITIAL, null, 0L, 2, 6);
        failed.fail();
        matchingRoundRepository.save(failed);

        mockMvc.perform(get("/api/v1/matchings/positions/" + POSITION_ID + "/candidates")
                        .cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failed").value(true))
                .andExpect(jsonPath("$.data.preparing").value(false))
                .andExpect(jsonPath("$.data.candidates").isEmpty());
    }

    @Test
    @DisplayName("라운드가 없어도 남의 포지션은 볼 수 없다")
    void preparingResponseStillChecksOwnership() throws Exception {
        // 평소엔 라운드에서 projectId 를 얻어 소유자를 확인하는데, 라운드가 없으면 그 경로가 없다.
        // 확인을 건너뛰면 남의 프로젝트 모집 인원을 들여다볼 수 있다.
        //
        // **다른 클라이언트로 검증해야 한다.** 프리랜서로 부르면 컨트롤러의 hasRole('CLIENT')에서
        // 먼저 막혀서, 이 검사를 지워도 테스트가 통과한다(실제로 그렇게 짰다가 변이 테스트로 걸렀다).
        Cookie otherClientToken = signUpAndLoginOtherClient();

        mockMvc.perform(get("/api/v1/matchings/positions/" + POSITION_ID + "/candidates")
                        .cookie(otherClientToken))
                .andExpect(status().isForbidden());
    }

    /** 프로젝트를 소유하지 않은 두 번째 클라이언트. 소유자 검증 테스트에만 쓴다. */
    private Cookie signUpAndLoginOtherClient() throws Exception {
        String email = "other-client@pairing.com";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("companyName", "주식회사 남의회사");
        body.put("businessNo", "9876543210");
        body.put("businessField", "IT_CONTENTS_AI");
        body.put("employeeCount", "SIZE_10_49");
        body.put("address", "서울 마포구 월드컵북로 1");
        body.put("email", email);
        body.put("name", "박클라");
        body.put("phone", "010-3333-4444");
        body.put("password", PASSWORD);
        body.put("passwordConfirm", PASSWORD);
        body.put("card", Map.of("cardNumber", "9999-8888-7777-6666", "cardBrand", "국민카드"));
        body.put("bankAccount", Map.of("bankCode", "004", "accountNo", "110-999-888777", "accountHolder", "박클라"));
        body.put("agreements", List.of(
                Map.of("termsId", clientTermsId, "agreed", true),
                Map.of("termsId", privacyTermsId, "agreed", true),
                Map.of("termsId", marketingTermsId, "agreed", false)));

        mockMvc.perform(post("/api/v1/auth/signup/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","role":"CLIENT"}"""
                                .formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("accessToken");
    }

    @Test
    @DisplayName("재추천으로 회차가 늘어도 이전 회차 후보가 목록에서 사라지지 않는다")
    void candidateListAccumulatesAcrossRounds() throws Exception {
        // 클라이언트가 후보 2명을 받은 뒤, 선택도 거절도 하지 않고 유료 재추천으로 1명을 더 받은 상황.
        // 이때 화면에는 3명이 다 보여야 한다.
        //
        // 최신 회차만 보여주면 이전 2명이 사라지는데, R02 예외조건 5(같은 프로젝트에서 이미 추천된
        // 프리랜서는 다음 회차에서 제외)때문에 **다시 나올 방법이 없다.** 후보를 늘리려고 돈을 낸
        // 클라이언트가 오히려 후보를 잃는다.
        long secondFreelancerId = 7_009_101L;
        long thirdFreelancerId = 7_009_102L;
        seedFreelancerWithoutRequiredSkill(secondFreelancerId);
        seedFreelancerWithoutRequiredSkill(thirdFreelancerId);

        MatchingRound first = seedRound(2);
        seedExposedCandidate(first.getId(), 1);
        seedExposedCandidateFor(first.getId(), 2, secondFreelancerId);

        MatchingRound paid = seedRound(1, 2, RecommendationType.PAID, 1);
        seedExposedCandidateFor(paid.getId(), 1, thirdFreelancerId);

        mockMvc.perform(get("/api/v1/matchings/positions/" + POSITION_ID + "/candidates")
                        .cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates.length()").value(3))
                // 방금 재추천으로 받은 후보가 맨 위다 - 스크롤해서 찾게 하면 안 된다.
                .andExpect(jsonPath("$.data.candidates[0].freelancerId").value(thirdFreelancerId))
                .andExpect(jsonPath("$.data.candidates[1].freelancerId").value(freelancerAccountId))
                .andExpect(jsonPath("$.data.candidates[2].freelancerId").value(secondFreelancerId))
                // 머리말은 최신 회차 기준이다.
                .andExpect(jsonPath("$.data.roundNo").value(2))
                .andExpect(jsonPath("$.data.roundType").value("PAID"));
    }

    @Test
    @DisplayName("이전 회차 후보를 거절해도 목록과 회차 머리말이 과거로 되돌아가지 않는다")
    void rejectingOlderRoundCandidateKeepsLatestRoundHeader() throws Exception {
        long secondFreelancerId = 7_009_103L;
        seedFreelancerWithoutRequiredSkill(secondFreelancerId);

        MatchingRound first = seedRound(2);
        MatchingCandidate oldCandidate = seedExposedCandidate(first.getId(), 1);

        MatchingRound paid = seedRound(1, 2, RecommendationType.PAID, 1);
        seedExposedCandidateFor(paid.getId(), 1, secondFreelancerId);

        mockMvc.perform(post("/api/v1/matchings/candidates/" + oldCandidate.getId() + "/rejection")
                        .cookie(clientAccessToken))
                .andExpect(status().isOk())
                // 거절해도 목록에서 빠지지 않고 거절 표시만 붙는다(회차 단위 조회 때와 같은 규칙).
                .andExpect(jsonPath("$.data.candidates.length()").value(2))
                // 거절한 후보가 1회차 소속이라고 머리말이 1회차로 돌아가면, 화면의 재추천 버튼 상태까지 어긋난다.
                .andExpect(jsonPath("$.data.roundNo").value(2))
                .andExpect(jsonPath("$.data.roundType").value("PAID"));
    }

    private MatchingCandidate seedExposedCandidate(Long roundId, int rank, String guardReason) {
        MatchingCandidate candidate = MatchingCandidate.createFromEmbedding(roundId, POSITION_ID,
                freelancerAccountId, 0.8);
        candidate.applyLlmResult(88.0, "요구 스킬 3개 중 3개 일치|경력 조건 충족");
        candidate.applyGradeWeight(0.0);
        candidate.applyGuard(true, guardReason);
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
                .andExpect(jsonPath("$.data.candidates[0].rejected").value(false))
                .andExpect(jsonPath("$.data.candidates[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.candidates[0].statusLabel").value("선택 가능"))
                .andExpect(jsonPath("$.data.budgetWarned").value(false));
    }

    @Test
    @DisplayName("선택·거절한 후보도 목록에 남고 상태로 구분된다")
    void selectedAndRejectedCandidatesStayInTheListWithStatus() throws Exception {
        // 숨기지 않는 이유: 요청한 후보를 숨기면 누구에게 보냈는지 볼 수 없고, 거절은 되돌리는 API가
        // 없는 데다 R02 예외조건 5로 다음 회차에도 안 나와서 그 후보를 영구히 잃는다.
        long rejectedFreelancerId = 7_009_104L;
        seedFreelancerWithoutRequiredSkill(rejectedFreelancerId);

        MatchingRound round = seedRound(2);
        MatchingCandidate requestedCandidate = seedExposedCandidate(round.getId(), 1);
        MatchingCandidate rejectedCandidate = seedExposedCandidateFor(round.getId(), 2, rejectedFreelancerId);

        sendRequest(requestedCandidate.getId()).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/matchings/candidates/" + rejectedCandidate.getId() + "/rejection")
                        .cookie(clientAccessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/matchings/positions/" + POSITION_ID + "/candidates")
                        .cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates.length()").value(2))
                .andExpect(jsonPath("$.data.candidates[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.candidates[0].statusLabel").value("요청 보냄"))
                .andExpect(jsonPath("$.data.candidates[1].status").value("REJECTED"))
                .andExpect(jsonPath("$.data.candidates[1].statusLabel").value("거절함"));
    }

    @Test
    @DisplayName("요청을 보낸 후보는 거절할 수 없다 — 카드 상태는 셋 중 하나여야 한다")
    void requestedCandidateCannotBeRejected() throws Exception {
        MatchingRound round = seedRound(2);
        MatchingCandidate candidate = seedExposedCandidate(round.getId(), 1);

        sendRequest(candidate.getId()).andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/matchings/candidates/" + candidate.getId() + "/rejection")
                        .cookie(clientAccessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MT_019"));

        // 상태가 "요청 보냄"에서 흔들리지 않는다.
        mockMvc.perform(get("/api/v1/matchings/positions/" + POSITION_ID + "/candidates")
                        .cookie(clientAccessToken))
                .andExpect(jsonPath("$.data.candidates[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.candidates[0].rejected").value(false));
    }

    @Test
    @DisplayName("거절한 후보에게는 요청을 보낼 수 없다 — 반대 방향도 막혀야 셋이 배타적이다")
    void rejectedCandidateCannotBeRequested() throws Exception {
        MatchingRound round = seedRound(2);
        MatchingCandidate candidate = seedExposedCandidate(round.getId(), 1);

        mockMvc.perform(post("/api/v1/matchings/candidates/" + candidate.getId() + "/rejection")
                        .cookie(clientAccessToken))
                .andExpect(status().isOk());

        sendRequest(candidate.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MT_017"));
    }

    @Test
    @DisplayName("노출 후보에 예산 가드 사유가 있으면 예산 경고를 표시한다")
    void candidateListMarksBudgetWarnedWhenExposedCandidateHasGuardReason() throws Exception {
        MatchingRound round = seedRound(2);
        seedExposedCandidate(round.getId(), 1, "예산 조합 초과");

        mockMvc.perform(get("/api/v1/matchings/positions/" + POSITION_ID + "/candidates")
                        .cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.budgetWarned").value(true));
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

    /** 화면에 안 나오는 후보(가드 탈락 또는 대기 순번). 클라이언트는 이 candidateId를 알 수 없어야 정상이다. */
    private MatchingCandidate seedHiddenCandidate(Long roundId, boolean guardPassed) {
        return seedHiddenCandidate(roundId, freelancerAccountId, guardPassed);
    }

    private MatchingCandidate seedHiddenCandidate(Long roundId, Long freelancerId, boolean guardPassed) {
        MatchingCandidate candidate = MatchingCandidate.createFromEmbedding(roundId, POSITION_ID,
                freelancerId, 0.8);
        candidate.applyLlmResult(88.0, "요구 스킬 3개 중 3개 일치|경력 조건 충족");
        candidate.applyGradeWeight(0.0);
        candidate.applyGuard(guardPassed, guardPassed ? null : "직무 불일치: FRONTEND");
        return matchingCandidateRepository.save(candidate);
    }

    private ResultActions sendRequest(Long candidateId) throws Exception {
        Map<String, Object> body = Map.of("positionId", POSITION_ID, "candidateIds", List.of(candidateId));
        return mockMvc.perform(post("/api/v1/matchings/requests")
                .cookie(clientAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    @Test
    @DisplayName("가드에 떨어진 후보에게는 candidateId를 직접 넣어도 요청이 나가지 않는다")
    void sendRequestRejectsGuardFailedCandidate() throws Exception {
        MatchingRound round = seedRound(2);
        // 이 검증이 없으면 화면에 노출되지 않은 후보(가드 탈락자 포함)에게 candidateId 직접 입력으로 요청이 나간다.
        MatchingCandidate candidate = seedHiddenCandidate(round.getId(), false);

        sendRequest(candidate.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MT_017"));
    }

    @Test
    @DisplayName("노출 인원 밖(대기 순번) 후보에게는 요청이 나가지 않는다")
    void sendRequestRejectsNotExposedCandidate() throws Exception {
        MatchingRound round = seedRound(2);
        MatchingCandidate candidate = seedHiddenCandidate(round.getId(), true);

        sendRequest(candidate.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MT_017"));
    }

    @Test
    @DisplayName("클라이언트가 이미 내린 후보에게는 요청이 나가지 않는다")
    void sendRequestRejectsAlreadyRejectedCandidate() throws Exception {
        MatchingRound round = seedRound(2);
        MatchingCandidate candidate = seedExposedCandidate(round.getId(), 1);

        mockMvc.perform(post("/api/v1/matchings/candidates/" + candidate.getId() + "/rejection")
                        .cookie(clientAccessToken))
                .andExpect(status().isOk());

        sendRequest(candidate.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MT_017"));
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

    @Test
    @DisplayName("요청 상세는 클라이언트와 프리랜서 양쪽 다 조회할 수 있다")
    void requestDetailIsReadableByBothParties() throws Exception {
        MatchingRound round = seedRound(2);
        MatchingCandidate candidate = seedExposedCandidate(round.getId(), 1);
        Long requestId = sendRequestAndGetId(candidate.getId());

        // 이 자리에서 두 번 사고가 났다. 당사자 판별에 "던지는 조회"를 쓰면 반대편이 늘 404를 받는데,
        // 한쪽만 검증하면 그 사고를 못 잡는다. 그래서 두 방향을 한 테스트에 묶어둔다.
        //   2026-08-09 클라이언트가 MT_015 / 2026-08-13 프리랜서가 AC_002
        mockMvc.perform(get("/api/v1/matchings/requests/" + requestId).cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value(requestId));

        mockMvc.perform(get("/api/v1/matchings/requests/" + requestId).cookie(freelancerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value(requestId));
    }

    @Test
    @DisplayName("주요 담당 업무는 요청 상세에서만 보이고 목록/받은요청에서는 안 보인다")
    void mainTaskIsExposedOnlyInRequestDetailNotInLists() throws Exception {
        MatchingRound round = seedRound(2);
        MatchingCandidate candidate = seedExposedCandidate(round.getId(), 1);
        Long requestId = sendRequestAndGetId(candidate.getId());

        mockMvc.perform(get("/api/v1/matchings/requests/" + requestId)
                        .cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mainTask").value("주문 시스템 API 개발"));

        mockMvc.perform(get("/api/v1/matchings/requests")
                        .param("projectId", String.valueOf(PROJECT_ID))
                        .cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].mainTask").doesNotExist());

        mockMvc.perform(get("/api/v1/matchings/requests/received")
                        .cookie(freelancerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].mainTask").doesNotExist());
    }

    /** 특정 계정에게 실제로 저장된 알림 제목들. 알림 도메인 API를 거치지 않고 테이블을 직접 본다. */
    private List<String> notificationTitles(Long ownerAccountId, String type) {
        return jdbcTemplate.queryForList(
                "SELECT title FROM notification WHERE owner_account_id = ? AND type = ?",
                String.class, ownerAccountId, type);
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
        // 수락 사실은 클라이언트에게 알림으로 가야 한다(요청을 보낸 쪽이 결과를 알아야 하므로).
        assertThat(notificationTitles(clientAccountId, "MATCHING_ACCEPTED"))
                .anyMatch(title -> title.contains("수락"));
    }

    @Test
    @DisplayName("조건이 전부 맞으면 즉시 타결되고, 수락 응답도 계약 대기로 나간다")
    void acceptWithNoMismatchReturnsContractPending() throws Exception {
        // 프로젝트 조건을 프리랜서 조건(REMOTE/FULL_TIME/6개월/월 650만)에 맞춰 불일치를 0개로 만든다.
        // 예산은 budgetCap(순예산 ÷ 인원 ÷ 개월)이 희망 단가를 넘도록 올린다.
        jdbcTemplate.update("UPDATE project SET work_style = 'REMOTE', budget_amount = ?, "
                        + "start_desired_date = ? WHERE id = ?",
                200_000_000L, LocalDate.now().plusDays(14), PROJECT_ID);

        MatchingRound round = seedRound(2);
        MatchingCandidate candidate = seedExposedCandidate(round.getId(), 1);
        Long requestId = sendRequestAndGetId(candidate.getId());

        // 협상 라운드를 돌지 않고 바로 타결되므로 CONTRACT_PENDING 이 나가야 한다.
        // 응답을 저장 전 객체로 만들면 DB(CONTRACT_PENDING)와 다른 NEGOTIATING 이 나간다.
        mockMvc.perform(post("/api/v1/matchings/requests/" + requestId + "/acceptance")
                        .cookie(freelancerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONTRACT_PENDING"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM matching_request WHERE id = ?", String.class, requestId))
                .isEqualTo("CONTRACT_PENDING");
    }

    @Test
    @DisplayName("매칭 요청을 보내면 받은 프리랜서에게 알림이 간다")
    void sendingRequestNotifiesFreelancer() throws Exception {
        MatchingRound round = seedRound(2);
        MatchingCandidate candidate = seedExposedCandidate(round.getId(), 1);

        sendRequestAndGetId(candidate.getId());

        assertThat(notificationTitles(freelancerAccountId, "MATCHING_REQUESTED")).isNotEmpty();
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
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("DIRECT_REJECT"));

        assertThat(notificationTitles(clientAccountId, "MATCHING_REJECTED"))
                .anyMatch(title -> title.contains("거절"));
    }

    @Test
    @DisplayName("모집 인원이 다 찬 포지션은 진행중이어도 재추천을 막는다")
    void rerecommendBlockedWhenPositionAlreadyFilled() throws Exception {
        MatchingRound round = seedRound(2);
        MatchingCandidate candidate = seedExposedCandidate(round.getId(), 1);
        Long requestId = sendRequestAndGetId(candidate.getId());

        // 계약까지 가서 자리를 차지한 상태. headcount를 1로 줄여 "자리가 다 찬 포지션"을 만든다.
        jdbcTemplate.update("UPDATE matching_request SET status = 'IN_PROGRESS' WHERE id = ?", requestId);
        jdbcTemplate.update("UPDATE project_position SET headcount = 1 WHERE id = ?", POSITION_ID);

        // 막지 않으면 유료 재추천이 결제되고, 정작 그 결과로 요청을 보낼 때 인원 초과(MT_005)로 막힌다.
        mockMvc.perform(post("/api/v1/matchings/positions/" + POSITION_ID + "/rerecommendations")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PAID","quantity":1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MT_018"));
    }

    @Test
    @DisplayName("남은 자리보다 많은 인원으로 유료 재추천하면 결제 전에 막는다")
    void paidRerecommendBlockedWhenQuantityExceedsVacancy() throws Exception {
        MatchingRound round = seedRound(2);
        MatchingCandidate candidate = seedExposedCandidate(round.getId(), 1);
        Long requestId = sendRequestAndGetId(candidate.getId());

        // headcount 2 중 1자리를 이미 쓰고 있으므로 남은 자리는 1이다.
        jdbcTemplate.update("UPDATE matching_request SET status = 'IN_PROGRESS' WHERE id = ?", requestId);

        mockMvc.perform(post("/api/v1/matchings/positions/" + POSITION_ID + "/rerecommendations")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PAID","quantity":2}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MT_005"));
    }

    @Test
    @DisplayName("중도 종료로 자리가 다시 비면 진행중인 프로젝트에서도 재추천할 수 있다")
    void rerecommendAllowedWhenTerminationReopensSlot() throws Exception {
        MatchingRound round = seedRound(2);
        MatchingCandidate candidate = seedExposedCandidate(round.getId(), 1);
        Long requestId = sendRequestAndGetId(candidate.getId());

        // "2/3명 진행 중 · 1명 계약 종료" 화면. 상태(진행중)로 막았다면 여기서 다시 못 뽑는다.
        jdbcTemplate.update("UPDATE matching_request SET status = 'TERMINATED' WHERE id = ?", requestId);
        jdbcTemplate.update("UPDATE project_position SET headcount = 1 WHERE id = ?", POSITION_ID);
        jdbcTemplate.update("UPDATE project SET status = 'IN_PROGRESS' WHERE id = ?", PROJECT_ID);

        given(matchingPort.recommend(eq(POSITION_ID), eq(1), eq(3), eq(List.of(freelancerAccountId)), anyLong()))
                .willReturn(new MatchingRecommendation(POSITION_ID, "gemini-2.0-flash", List.of()));

        mockMvc.perform(post("/api/v1/matchings/positions/" + POSITION_ID + "/rerecommendations")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PAID","quantity":1}"""))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("계약 후 중도 종료·협상 결렬은 무료 재추천 조건에 포함되지 않는다(P41)")
    void terminatedOrNegotiationFailedDoesNotUnlockFreeRerecommend() throws Exception {
        MatchingRound round = seedRound(2);
        MatchingCandidate candidate = seedExposedCandidate(round.getId(), 1);
        Long requestId = sendRequestAndGetId(candidate.getId());

        // P41: "협상 결렬, 계약 전 파기, 계약 후 중도 종료는 무료 재추천 조건에 포함하지 않는다."
        // 전원 거절·만료된 경우에만 열어줘야 하므로, 아래 두 상태에서는 계속 막혀야 한다.
        for (String status : List.of("TERMINATED", "NEGOTIATION_FAILED")) {
            jdbcTemplate.update("UPDATE matching_request SET status = ? WHERE id = ?", status, requestId);

            mockMvc.perform(post("/api/v1/matchings/positions/" + POSITION_ID + "/rerecommendations")
                            .cookie(clientAccessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"type":"FREE"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("MT_008"));
        }

        // 거절(만료 포함)이면 그때는 열린다.
        jdbcTemplate.update("UPDATE matching_request SET status = 'REJECTED' WHERE id = ?", requestId);
        given(matchingPort.recommend(eq(POSITION_ID), eq(2), eq(3), eq(List.of(freelancerAccountId)), anyLong()))
                .willReturn(new MatchingRecommendation(POSITION_ID, "gemini-3.5-flash", List.of()));

        mockMvc.perform(post("/api/v1/matchings/positions/" + POSITION_ID + "/rerecommendations")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"FREE"}"""))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("응답 기한이 지난 요청은 스케줄러가 거절(만료) 처리하고, 그제서야 무료 재추천을 쓸 수 있다")
    void expiredPendingRequestIsAutoExpiredAndUnblocksFreeRerecommend() throws Exception {
        MatchingRound round = seedRound(2);
        MatchingCandidate candidate = seedExposedCandidate(round.getId(), 1);
        Long requestId = sendRequestAndGetId(candidate.getId());

        // 응답 대기중인 요청이 남아있는 동안은 무료 재추천이 막힌다(P41).
        mockMvc.perform(post("/api/v1/matchings/positions/" + POSITION_ID + "/rerecommendations")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"FREE"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MT_008"));

        jdbcTemplate.update("UPDATE matching_request SET expires_at = ? WHERE id = ?",
                LocalDateTime.now().minusDays(1), requestId);

        int expiredCount = matchingRequestCommandUseCase.expireOverdueRequests();
        assertThat(expiredCount).isEqualTo(1);

        // 직접 거절과 구분되게 사유가 EXPIRED로 응답에 내려와야 한다.
        mockMvc.perform(get("/api/v1/matchings/requests/" + requestId).cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("EXPIRED"));

        given(matchingPort.recommend(eq(POSITION_ID), eq(2), eq(3), eq(List.of(freelancerAccountId)), anyLong()))
                .willReturn(new MatchingRecommendation(POSITION_ID, "gemini-2.0-flash", List.of()));

        // 만료 처리 후에는 "전원 거절"로 간주돼 무료 재추천을 쓸 수 있다.
        mockMvc.perform(post("/api/v1/matchings/positions/" + POSITION_ID + "/rerecommendations")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"FREE"}"""))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("스케줄러가 아직 안 돈 사이에 기한 지난 요청을 수락하려 하면 그 자리에서 만료 처리하고 막는다")
    void acceptOnOverdueRequestExpiresItOnTheSpotInsteadOfAccepting() throws Exception {
        MatchingRound round = seedRound(2);
        MatchingCandidate candidate = seedExposedCandidate(round.getId(), 1);
        Long requestId = sendRequestAndGetId(candidate.getId());

        // 스케줄러(expireOverdueRequests)를 부르지 않고, 기한만 지난 상태를 만든다.
        jdbcTemplate.update("UPDATE matching_request SET expires_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(1), requestId);

        mockMvc.perform(post("/api/v1/matchings/requests/" + requestId + "/acceptance")
                        .cookie(freelancerAccessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MT_016"));

        mockMvc.perform(get("/api/v1/matchings/requests/" + requestId).cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    @DisplayName("재추천을 요청하면 AI 서버(Pairing-python) 응답으로 새 회차/후보가 만들어진다")
    void rerecommendCreatesNewRoundFromAiServerResponse() throws Exception {
        seedRound(2);
        given(matchingPort.recommend(eq(POSITION_ID), eq(2), eq(3), eq(List.of()), anyLong()))
                .willReturn(new MatchingRecommendation(POSITION_ID, "gemini-2.0-flash",
                        List.of(new RankedFreelancer(freelancerAccountId, 91.0,
                                "요구 스킬 3개 중 3개 일치|경력 조건 충족", 0.82))));

        mockMvc.perform(post("/api/v1/matchings/positions/" + POSITION_ID + "/rerecommendations")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PAID","quantity":2}"""))
                // 후보 목록은 이 응답에 없다 — AI 호출이 끝난 뒤 비동기로 채워지고 알림으로 알려준다.
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/matchings/positions/" + POSITION_ID + "/candidates")
                        .cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates.length()").value(1))
                .andExpect(jsonPath("$.data.candidates[0].name").value("이프리"));
    }

    @Test
    @DisplayName("재추천 시 이전 회차에서 이미 노출됐던 프리랜서 id를 AI 서버 호출의 제외 목록으로 넘긴다")
    void rerecommendPassesPreviouslySurfacedFreelancerIdsAsExcluded() throws Exception {
        MatchingRound firstRound = seedRound(2);
        seedExposedCandidate(firstRound.getId(), 1);
        seedHiddenCandidate(firstRound.getId(), 999_998L, true);

        given(matchingPort.recommend(eq(POSITION_ID), eq(2), eq(3), eq(List.of(freelancerAccountId)), anyLong()))
                .willReturn(new MatchingRecommendation(POSITION_ID, "gemini-2.0-flash", List.of()));

        mockMvc.perform(post("/api/v1/matchings/positions/" + POSITION_ID + "/rerecommendations")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PAID","quantity":2}"""))
                .andExpect(status().isAccepted());

        // 마지막 인자는 budgetCap: 총예산 6000만 - 수수료 10% = 5400만 ÷ 총인원 2 ÷ 6개월 = 450만.
        verify(matchingPort).recommend(POSITION_ID, 2, 3, List.of(freelancerAccountId), 4_500_000L);
    }

    @Test
    @DisplayName("스킬이 부족해도 가드는 후보를 떨어뜨리지 않는다 — 직무·스킬 재검증은 뺐다")
    void guardNoLongerRejectsCandidatesOnSkillMismatch() throws Exception {
        seedRound(2);
        long partialSkillFreelancerId = 7_009_001L;
        seedFreelancerWithoutRequiredSkill(partialSkillFreelancerId);

        given(matchingPort.recommend(eq(POSITION_ID), eq(2), eq(3), eq(List.of()), anyLong())).willReturn(
                new MatchingRecommendation(POSITION_ID, "gemini-2.0-flash", List.of(
                        new RankedFreelancer(partialSkillFreelancerId, 95.0, "경력 우수", 0.90),
                        new RankedFreelancer(freelancerAccountId, 80.0, "요구 스킬 3개 중 3개 일치", 0.75))));

        mockMvc.perform(post("/api/v1/matchings/positions/" + POSITION_ID + "/rerecommendations")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PAID","quantity":2}"""))
                .andExpect(status().isAccepted());

        // 하드필터를 통과한 사람만 LLM에 가므로 가드에서 또 볼 필요가 없다. 둘 다 노출돼야 한다.
        // 스킬 부족은 조건점수 30점이 이미 깎았고, 부분 일치 후보 노출은 정책 P09가 허용한다.
        mockMvc.perform(get("/api/v1/matchings/positions/" + POSITION_ID + "/candidates")
                        .cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates.length()").value(2));
    }

    @Test
    @DisplayName("가드 G4: 중복 후보와 추천 근거 없는 후보를 버리고, 유사도는 그대로 저장한다")
    void guardDropsDuplicateAndReasonlessCandidates() throws Exception {
        seedRound(2);
        long otherFreelancerId = 7_009_002L;
        seedFreelancerWithoutRequiredSkill(otherFreelancerId);

        given(matchingPort.recommend(eq(POSITION_ID), eq(2), eq(3), eq(List.of()), anyLong())).willReturn(
                new MatchingRecommendation(POSITION_ID, "gemini-2.0-flash", List.of(
                        new RankedFreelancer(freelancerAccountId, 91.0, "요구 스킬 일치", 0.8125),
                        // 같은 사람을 두 번 — 뒤엣것은 버려야 한다.
                        new RankedFreelancer(freelancerAccountId, 70.0, "중복", 0.8125),
                        // 추천 근거가 비어 있으면 화면에 근거 없는 후보가 뜬다.
                        new RankedFreelancer(otherFreelancerId, 88.0, "  ", 0.70))));

        mockMvc.perform(post("/api/v1/matchings/positions/" + POSITION_ID + "/rerecommendations")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PAID","quantity":2}"""))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/matchings/positions/" + POSITION_ID + "/candidates")
                        .cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates.length()").value(1));

        // AI 서버가 준 유사도가 그대로 저장돼야 한다. 예전엔 이 자리에 0.0이 박혀 있어서
        // "이 후보가 왜 뽑혔나"를 되짚을 수 없었다.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT similarity FROM matching_candidate WHERE freelancer_id = ? ORDER BY id DESC LIMIT 1",
                Double.class, freelancerAccountId))
                .isEqualTo(0.8125);
    }

    /** 포지션이 요구하는 SPRING_BOOT를 안 갖춘 프리랜서. 하드필터(파이썬)를 통과했다고 가정한다. */
    private void seedFreelancerWithoutRequiredSkill(long freelancerId) {
        jdbcTemplate.update(
                "INSERT INTO account (id, email, role, name, phone, signup_type, status, email_verified, "
                        + "login_fail_count, is_temp_password) "
                        + "VALUES (?, ?, 'FREELANCER', ?, ?, 'EMAIL', 'ACTIVE', true, 0, false)",
                freelancerId, "freelancer-" + freelancerId + "@pairing.com", "박프리", "010-9999-0002");
        jdbcTemplate.update(
                "INSERT INTO freelancer_profile (id, account_id, birth_date, ai_matching_agreed, grade) "
                        + "VALUES (?, ?, ?, true, 'JUNIOR')",
                freelancerId, freelancerId, LocalDate.of(1998, 5, 5));
        freelancerConditionUseCase.upsert(new UpsertConditionCommand(
                freelancerId, JobCategory.DEVELOPMENT, JobRole.BACKEND, null,
                WorkStyle.REMOTE, WorkForm.FULL_TIME, PayUnit.MONTHLY, 4_000_000L, 3_500_000L,
                LocalDate.now().plusDays(14), false, 6, PeriodUnit.MONTH, true, 2,
                List.of(new UpsertConditionCommand.Skill(SkillCode.PYTHON, SkillLevel.ADVANCED))));
    }

    @Test
    @DisplayName("조건에 맞는 후보가 없으면 장애가 아니라 후보 소진으로 닫고 그렇게 안내한다")
    void rerecommendEmptyPoolIsExhaustedNotFailed() throws Exception {
        seedRound(2);
        // AI 서버가 "조건에 맞는 후보 없음"으로 답한 상황(어댑터가 빈 결과로 바꿔서 넘긴다).
        given(matchingPort.recommend(eq(POSITION_ID), eq(2), eq(3), eq(List.of()), anyLong()))
                .willReturn(new MatchingRecommendation(POSITION_ID, "gemini-3.5-flash", List.of()));

        mockMvc.perform(post("/api/v1/matchings/positions/" + POSITION_ID + "/rerecommendations")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PAID","quantity":2}"""))
                .andExpect(status().isAccepted());

        // 장애가 아니므로 FAILED 가 아니라 EXHAUSTED 로 닫혀야 한다.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM matching_round WHERE position_id = ? ORDER BY id DESC LIMIT 1",
                String.class, POSITION_ID))
                .isEqualTo("EXHAUSTED");

        // 안내 문구도 "일시적 오류, 다시 시도"가 아니라 "더 없다"여야 한다.
        // 후보는 다시 시도해도 안 생기므로 재시도를 권하면 사용자가 계속 누르게 된다.
        assertThat(notificationTitles(clientAccountId, "MATCHING_RECOMMENDED"))
                .anyMatch(title -> title.contains("더 없습니다"));
    }

    @Test
    @DisplayName("AI 호출이 실패하면 회차를 FAILED로 닫고, 그 회차는 재추천 한도를 쓴 걸로 치지 않는다")
    void rerecommendMarksRoundFailedAndDoesNotConsumeQuotaWhenAiFails() throws Exception {
        seedRound(2);
        given(matchingPort.recommend(eq(POSITION_ID), eq(2), eq(3), eq(List.of()), anyLong()))
                .willThrow(new IllegalStateException("AI 서버 응답 없음"));

        // 재추천 요청 자체는 접수된다(실패는 비동기 처리 뒤에 알림으로 알려준다).
        mockMvc.perform(post("/api/v1/matchings/positions/" + POSITION_ID + "/rerecommendations")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PAID","quantity":2}"""))
                .andExpect(status().isAccepted());

        // 회차가 RUNNING으로 방치되지 않고 FAILED로 닫혀야 한다.
        Integer failedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM matching_round WHERE position_id = ? AND status = 'FAILED'",
                Integer.class, POSITION_ID);
        assertThat(failedCount).isEqualTo(1);

        // 실패한 회차는 한도를 쓴 게 아니므로 유료 재추천을 다시 시도할 수 있어야 한다.
        given(matchingPort.recommend(eq(POSITION_ID), eq(2), eq(3), eq(List.of()), anyLong()))
                .willReturn(new MatchingRecommendation(POSITION_ID, "gemini-3.5-flash",
                        List.of(new RankedFreelancer(freelancerAccountId, 91.0, "경력 조건 충족", 0.82))));

        mockMvc.perform(post("/api/v1/matchings/positions/" + POSITION_ID + "/rerecommendations")
                        .cookie(clientAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PAID","quantity":2}"""))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/matchings/positions/" + POSITION_ID + "/candidates")
                        .cookie(clientAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates.length()").value(1))
                // 유료 5회 중 성공한 1회만 차감돼야 한다(실패한 회차는 안 셈).
                .andExpect(jsonPath("$.data.paidRerecommendRemaining").value(4));
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
