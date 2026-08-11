package com.pairing.matching.application.service;

import com.pairing.freelancer.application.command.UpsertConditionCommand;
import com.pairing.freelancer.application.command.UpsertResumeCommand;
import com.pairing.freelancer.application.usecase.FreelancerConditionUseCase;
import com.pairing.freelancer.application.usecase.ResumeUseCase;
import com.pairing.freelancer.domain.model.GraduationStatus;
import com.pairing.global.config.SyncTaskExecutorTestConfig;
import com.pairing.matching.application.port.out.MatchingPort;
import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.SkillLevel;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

/**
 * 조건(직무·스킬·경력·근무방식·기간)을 저장하면 프리랜서 임베딩이 그 값으로 다시 올라가는지 확인한다.
 *
 * <p>2026-08-11 전에는 이 연결이 없었고, 없어도 겉으로는 멀쩡했다 — 임베딩 텍스트에 조건이 아예 안
 * 들어가서 다시 만들어도 같은 벡터가 나왔기 때문이다. 텍스트에 조건을 넣은 순간부터 이 리스너가
 * 없으면 <b>조건을 고쳐도 매칭은 옛날 조건으로 계속 돈다</b>.
 */
@Import(SyncTaskExecutorTestConfig.class)
@SpringBootTest
class ConditionUpdatedEventListenerTest {

    private static final Long ACCOUNT_ID = 7402L;

    @Autowired
    private FreelancerConditionUseCase freelancerConditionUseCase;
    @Autowired
    private ResumeUseCase resumeUseCase;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private MatchingPort matchingPort;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM freelancer_profile WHERE id = ?", ACCOUNT_ID);
        jdbcTemplate.update("DELETE FROM account WHERE id = ?", ACCOUNT_ID);
        jdbcTemplate.update(
                "INSERT INTO account (id, email, role, name, phone, signup_type, status, email_verified, "
                        + "login_fail_count, is_temp_password) "
                        + "VALUES (?, ?, 'FREELANCER', ?, ?, 'EMAIL', 'ACTIVE', true, 0, false)",
                ACCOUNT_ID, "condition-updated-" + ACCOUNT_ID + "@pairing.com", "이프리", "010-4444-6666");
        // freelancer_profile.id를 account.id와 같게 심어 resolveFreelancerId(ACCOUNT_ID) == ACCOUNT_ID.
        jdbcTemplate.update(
                "INSERT INTO freelancer_profile (id, account_id, birth_date, ai_matching_agreed, grade) "
                        + "VALUES (?, ?, ?, true, 'JUNIOR')",
                ACCOUNT_ID, ACCOUNT_ID, LocalDate.of(1995, 3, 1));
        // 임베딩 텍스트는 이력서(자기소개)까지 같이 조립하므로 이력서가 있어야 한다.
        // 이 저장도 ResumeUpdatedEvent로 임베딩을 한 번 올리므로, 아래 검증이 그것까지 세지 않게
        // 여기서 호출 기록을 지운다 — 검증 대상은 "조건 저장이 일으킨" 재생성뿐이다.
        resumeUseCase.upsert(resumeCommand());
        clearInvocations(matchingPort);
    }

    @Test
    @DisplayName("조건을 저장하면 스킬·경력·근무방식이 들어간 텍스트로 임베딩을 다시 올린다")
    void savingConditionRefreshesFreelancerEmbedding() {
        freelancerConditionUseCase.upsert(conditionCommand());

        verify(matchingPort).upsertFreelancerEmbedding(eq(ACCOUNT_ID), argThat(text ->
                text.contains("Java")
                        && text.contains("Spring Boot")
                        && text.contains("경력 5년")
                        && text.contains("재택 · 풀타임")
                        && text.contains("6개월")
                        // 이력서 내용도 그대로 남아 있어야 한다(조건이 덮어쓰는 게 아니다).
                        && text.contains("백엔드 6년차입니다")));
    }

    @Test
    @DisplayName("단가와 시작 가능일은 임베딩 텍스트에 넣지 않는다")
    void payAndAvailableDateAreNotEmbedded() {
        freelancerConditionUseCase.upsert(conditionCommand());

        // 숫자는 임베딩으로 비교가 안 돼서 일부러 뺀다(FreelancerEmbeddingTextBuilder 주석).
        // Stage E(LLM)가 원본 값을 보고 감점으로 처리한다.
        verify(matchingPort).upsertFreelancerEmbedding(eq(ACCOUNT_ID), argThat(text ->
                !text.contains("6500000") && !text.contains("2026-09-01")));
    }

    private UpsertConditionCommand conditionCommand() {
        return new UpsertConditionCommand(
                ACCOUNT_ID, JobCategory.DEVELOPMENT, JobRole.BACKEND, null,
                WorkStyle.REMOTE, WorkForm.FULL_TIME, PayUnit.MONTHLY, 6_500_000L, 5_500_000L,
                LocalDate.of(2026, 9, 1), false, 6, PeriodUnit.MONTH, true, 5,
                List.of(new UpsertConditionCommand.Skill(SkillCode.JAVA, SkillLevel.ADVANCED),
                        new UpsertConditionCommand.Skill(SkillCode.SPRING_BOOT, SkillLevel.ADVANCED)));
    }

    private UpsertResumeCommand resumeCommand() {
        return new UpsertResumeCommand(
                ACCOUNT_ID, 1L, null, null, "06234", "서울 강남구", null, "백엔드 6년차입니다.", 1L,
                List.of(new UpsertResumeCommand.Education(LocalDate.of(2014, 3, 1), LocalDate.of(2018, 2, 1),
                        "페어링대학교", "컴퓨터공학과", GraduationStatus.GRADUATED, null)),
                List.of(new UpsertResumeCommand.Career(LocalDate.of(2018, 3, 1), null, "A사",
                        "백엔드팀 대리", "주문 시스템 개발")),
                List.of(),
                List.of(),
                new UpsertResumeCommand.Agreements(true, true, true, true));
    }
}
