package com.pairing.matching.application.service;

import com.pairing.freelancer.application.command.UpsertResumeCommand;
import com.pairing.freelancer.application.usecase.ResumeUseCase;
import com.pairing.freelancer.domain.model.GraduationStatus;
import com.pairing.matching.application.port.out.MatchingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.pairing.global.config.SyncTaskExecutorTestConfig;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * 이력서 저장이 {@link com.pairing.freelancer.application.event.ResumeUpdatedEvent}를 거쳐
 * 프리랜서 임베딩(자기소개+경력사항)을 실제로 다시 올리는지 확인한다.
 *
 * <p>이전엔 이 연결이 아예 없어서(HANDOFF 참고) freelancer_embedding 이 실환경에서 영원히
 * 비어 있는 상태였다 — Stage C(임베딩 랭킹)가 검색할 대상 자체가 없었다는 뜻이다.
 */
@Import(SyncTaskExecutorTestConfig.class)
@SpringBootTest
class ResumeUpdatedEventListenerTest {

    private static final Long ACCOUNT_ID = 7401L;

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
                ACCOUNT_ID, "resume-updated-" + ACCOUNT_ID + "@pairing.com", "이프리", "010-4444-5555");
        // freelancer_profile.id를 account.id와 같게 심어서 resolveFreelancerId(ACCOUNT_ID) == ACCOUNT_ID가
        // 되게 한다(다른 매칭 테스트들과 같은 트릭 — seedFreelancerProfile 참고).
        jdbcTemplate.update(
                "INSERT INTO freelancer_profile (id, account_id, birth_date, ai_matching_agreed, grade) "
                        + "VALUES (?, ?, ?, true, 'JUNIOR')",
                ACCOUNT_ID, ACCOUNT_ID, LocalDate.of(1995, 3, 1));
    }

    @Test
    @DisplayName("이력서를 저장하면 자기소개+경력사항으로 프리랜서 임베딩을 다시 올린다")
    void savingResumeRefreshesFreelancerEmbedding() {
        resumeUseCase.upsert(resumeCommand());

        verify(matchingPort).upsertFreelancerEmbedding(eq(ACCOUNT_ID),
                argThat(text -> text.contains("백엔드 6년차입니다") && text.contains("주문 시스템 개발")));
    }

    private UpsertResumeCommand resumeCommand() {
        return new UpsertResumeCommand(
                ACCOUNT_ID, 1L, null, null, "서울 강남구", "백엔드 6년차입니다.", 1L,
                List.of(new UpsertResumeCommand.Education(LocalDate.of(2014, 3, 1), LocalDate.of(2018, 2, 1),
                        "페어링대학교", "컴퓨터공학과", GraduationStatus.GRADUATED, null)),
                List.of(new UpsertResumeCommand.Career(LocalDate.of(2018, 3, 1), null, "A사",
                        "백엔드팀 대리", "주문 시스템 개발")),
                List.of(),
                List.of(),
                new UpsertResumeCommand.Agreements(true, true, true, true));
    }
}
