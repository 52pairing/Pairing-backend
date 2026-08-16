package com.pairing.matching.presentation.api;

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
import com.pairing.global.config.SyncTaskExecutorTestConfig;
import com.pairing.global.ratelimit.RateLimitProvider;
import com.pairing.matching.application.port.out.MatchingPort;
import com.pairing.matching.application.result.MatchingRecommendation;
import com.pairing.matching.application.result.RankedFreelancer;
import com.pairing.matching.application.service.StaleRoundRecoveryService;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.MatchingRoundStatus;
import com.pairing.matching.domain.model.MatchingSnapshot;
import com.pairing.matching.domain.model.RecommendationType;
import com.pairing.matching.domain.model.SnapshotType;
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
import com.pairing.terms.domain.model.TermsCode;
import com.pairing.terms.infrastructure.persistence.SpringDataTermsAgreementRepository;
import com.pairing.terms.infrastructure.persistence.SpringDataTermsRepository;
import com.pairing.terms.infrastructure.persistence.TermsJpaEntity;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * "스냅샷 저장 루프"(최적화 4번)의 개선 전/후를 실측한다. 노출된 후보마다
 * {@code findByFreelancerIdAndPositionIdAndSnapshotType}로 존재 확인을 개별로 하던 것을, 포지션
 * 단위로 한 번에 읽어 메모리에서 거르도록 바꿨다({@code MatchingRoundCreationService.saveFreelancerSnapshots}).
 *
 * <p>노출 인원 6명 중 3명은 이미 FREELANCER 스냅샷이 있고(재추천 등으로 이미 캡처된 상황을 재현),
 * 3명은 없다 — 실제로 겪는 "일부만 새로 캡처" 상황과 같다.
 */
@Import(SyncTaskExecutorTestConfig.class)
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class MatchingSnapshotSaveQueryCountTest {

    private static final String CLIENT_EMAIL = "snapshot-query-client@pairing.com";
    private static final String FREELANCER_EMAIL = "snapshot-query-freelancer@pairing.com";
    private static final String PASSWORD = "Passw0rd!";
    private static final Long PROJECT_ID = 7_201L;
    private static final Long POSITION_ID = 7_201L;

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
    private MatchingSnapshotRepository matchingSnapshotRepository;
    @Autowired
    private FreelancerConditionUseCase freelancerConditionUseCase;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StaleRoundRecoveryService staleRoundRecoveryService;
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
    private Long freelancerAccountId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM matching_request WHERE project_id = ?", PROJECT_ID);
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
        given(rateLimitProvider.getBucket(any(), anyString()))
                .willReturn(Bucket.builder().addLimit(Bandwidth.simple(1000, Duration.ofMinutes(1))).build());

        Long clientAccountId = signUpClient();
        freelancerAccountId = signUpFreelancer();
        Long clientProfileId = clientProfileRepository.findByAccountIdAndDeletedAtIsNull(clientAccountId)
                .orElseThrow().getId();
        seedProjectWithPosition(clientProfileId);

        // 노출 대상 6명: freelancerAccountId(실제 조건 있음) + 추가 5명(조건만 있는 간단 프리랜서).
        for (int i = 0; i < 5; i++) {
            seedSimpleFreelancer(8_201_000L + i);
        }
    }

    private Long saveTerms(TermsCode code, String title, boolean required, String targetRole) {
        return termsRepository.save(new TermsJpaEntity(
                null, code, code.getType(), "v1.0", title, "본문", required, targetRole,
                LocalDateTime.now().minusDays(1)
        )).getId();
    }

    private void seedProjectWithPosition(Long clientProfileId) {
        jdbcTemplate.update(
                "INSERT INTO project (id, client_id, title, start_desired_date, start_negotiable, "
                        + "period_value, period_unit, budget_amount, work_style, work_form, work_location, "
                        + "current_situation, main_task, detail_scope, extra_note, "
                        + "status, payment_status, total_headcount, confirmed_headcount, "
                        + "extension_count, free_rerecommend_used, paid_rerecommend_used) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                PROJECT_ID, clientProfileId, "스냅샷 쿼리 측정 프로젝트", LocalDate.now().plusDays(14), false,
                6, "MONTH", 60_000_000L, "ONSITE", "FULL_TIME", "서울 강남구",
                "현행 운영중", "백엔드 API 개발", "주문 도메인", "MSA 우대",
                "RECRUITING", "SUCCESS_FEE_PAID", 6, 0, 0, 0, 0);

        jdbcTemplate.update(
                "INSERT INTO project_position (id, project_id, position_no, job_category, job_role, "
                        + "min_career_years, headcount, confirmed_count, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                POSITION_ID, PROJECT_ID, 1, "DEVELOPMENT", "BACKEND", 3, 6, 0, "RECRUITING");

        jdbcTemplate.update(
                "INSERT INTO position_skill (position_id, skill_code) VALUES (?, ?)",
                POSITION_ID, "SPRING_BOOT");
    }

    private Long signUpClient() {
        jdbcTemplate.update(
                "INSERT INTO account (email, role, name, phone, signup_type, status, email_verified, "
                        + "login_fail_count, is_temp_password) "
                        + "VALUES (?, 'CLIENT', '김클라', '010-1111-2222', 'EMAIL', 'ACTIVE', true, 0, false)",
                CLIENT_EMAIL);
        Long accountId = accountRepository.findByEmailAndRoleAndDeletedAtIsNull(CLIENT_EMAIL,
                com.pairing.account.domain.model.Role.CLIENT).orElseThrow().getId();
        jdbcTemplate.update(
                "INSERT INTO client_profile (account_id, company_name, business_no, business_field, "
                        + "employee_count, grade) VALUES (?, '주식회사 페어링테크', '1234567890', 'IT_CONTENTS_AI', 'SIZE_10_49', 'SILVER')",
                accountId);
        return accountId;
    }

    private Long signUpFreelancer() {
        jdbcTemplate.update(
                "INSERT INTO account (email, role, name, phone, signup_type, status, email_verified, "
                        + "login_fail_count, is_temp_password) "
                        + "VALUES (?, 'FREELANCER', '이프리', '010-3333-4444', 'EMAIL', 'ACTIVE', true, 0, false)",
                FREELANCER_EMAIL);
        Long accountId = accountRepository.findByEmailAndRoleAndDeletedAtIsNull(FREELANCER_EMAIL,
                com.pairing.account.domain.model.Role.FREELANCER).orElseThrow().getId();
        jdbcTemplate.update(
                "INSERT INTO freelancer_profile (id, account_id, birth_date, ai_matching_agreed, grade) "
                        + "VALUES (?, ?, ?, true, 'SENIOR')",
                accountId, accountId, LocalDate.of(1995, 3, 1));
        freelancerConditionUseCase.upsert(new UpsertConditionCommand(
                accountId, JobCategory.DEVELOPMENT, JobRole.BACKEND,
                WorkStyle.REMOTE, WorkForm.FULL_TIME, PayUnit.MONTHLY, 6_500_000L, 5_500_000L,
                LocalDate.now().plusDays(14), false, 6, PeriodUnit.MONTH, true, 5,
                List.of(new UpsertConditionCommand.Skill(SkillCode.JAVA, SkillLevel.ADVANCED),
                        new UpsertConditionCommand.Skill(SkillCode.SPRING_BOOT, SkillLevel.ADVANCED))));
        return accountId;
    }

    /** 조건만 있는 간단한 프리랜서. resume은 없다 — findResume은 실패하지만, 지금 재는 건 존재 확인
     * 쿼리 수라 영향 없다(양쪽 다 똑같이 실패한다). */
    private void seedSimpleFreelancer(long freelancerId) {
        jdbcTemplate.update(
                "INSERT INTO account (id, email, role, name, phone, signup_type, status, email_verified, "
                        + "login_fail_count, is_temp_password) "
                        + "VALUES (?, ?, 'FREELANCER', '박프리', '010-9999-0002', 'EMAIL', 'ACTIVE', true, 0, false)",
                freelancerId, "freelancer-" + freelancerId + "@pairing.com");
        jdbcTemplate.update(
                "INSERT INTO freelancer_profile (id, account_id, birth_date, ai_matching_agreed, grade) "
                        + "VALUES (?, ?, ?, true, 'JUNIOR')",
                freelancerId, freelancerId, LocalDate.of(1998, 5, 5));
        freelancerConditionUseCase.upsert(new UpsertConditionCommand(
                freelancerId, JobCategory.DEVELOPMENT, JobRole.BACKEND,
                WorkStyle.REMOTE, WorkForm.FULL_TIME, PayUnit.MONTHLY, 4_000_000L, 3_500_000L,
                LocalDate.now().plusDays(14), false, 6, PeriodUnit.MONTH, true, 2,
                List.of(new UpsertConditionCommand.Skill(SkillCode.SPRING_BOOT, SkillLevel.ADVANCED))));
    }

    private MatchingRound seedStaleRunningRound(int exposeCount) {
        MatchingRound round = matchingRoundRepository.save(MatchingRound.create(PROJECT_ID, POSITION_ID, 1,
                RecommendationType.INITIAL, null, 0L, exposeCount, exposeCount * 3));
        jdbcTemplate.update("UPDATE matching_round SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusMinutes(30)), round.getId());
        return round;
    }

    @Test
    void printsQueryCountForSnapshotSaveLoop() {
        List<Long> exposedFreelancerIds = List.of(
                freelancerAccountId, 8_201_000L, 8_201_001L, 8_201_002L, 8_201_003L, 8_201_004L);

        // 6명 중 3명은 이미 이번 포지션에 FREELANCER 스냅샷이 있다고 미리 심는다(재추천 등으로 이미
        // 캡처된 상황) — 존재 확인이 실제로 걸러내는지까지 같이 확인한다.
        for (int i = 0; i < 3; i++) {
            matchingSnapshotRepository.save(MatchingSnapshot.create(PROJECT_ID, POSITION_ID,
                    exposedFreelancerIds.get(i), SnapshotType.FREELANCER, "{\"already\":true}"));
        }

        MatchingRound stale = seedStaleRunningRound(6);
        given(matchingPort.recommend(eq(POSITION_ID), eq(6), eq(3), eq(List.of()), anyLong()))
                .willReturn(new MatchingRecommendation(POSITION_ID, "gemini-3.5-flash",
                        exposedFreelancerIds.stream()
                                .map(id -> new RankedFreelancer(id, 88.0, "요구 스킬 일치", 0.8))
                                .toList()));

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        staleRoundRecoveryService.recoverStaleRounds();

        long queryCount = statistics.getPrepareStatementCount();
        System.out.println("=====SNAPSHOT_QUERY_COUNT===== 노출 6명(기존 스냅샷 3명 포함) 회차 채우기 -> "
                + queryCount + " prepared statements =====SNAPSHOT_QUERY_COUNT=====");

        MatchingRound refilled = matchingRoundRepository.findById(stale.getId()).orElseThrow();
        System.out.println("=====SNAPSHOT_QUERY_COUNT_STATUS===== " + refilled.getStatus()
                + " =====SNAPSHOT_QUERY_COUNT_STATUS=====");
    }
}
