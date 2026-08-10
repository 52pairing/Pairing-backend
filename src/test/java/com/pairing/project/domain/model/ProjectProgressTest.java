package com.pairing.project.domain.model;

import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 진행중 전환 규칙. 서명만으로는 넘어가지 않고 프리랜서 착수금 결제까지 끝나야 한다. (P27)
 */
class ProjectProgressTest {

    private Position position(long id, int headcount, int confirmedCount) {
        return Position.reconstitute(id, (int) id, JobCategory.DEVELOPMENT, JobRole.BACKEND,
                1, headcount, confirmedCount, PositionStatus.RECRUITING, null,
                List.of(SkillCode.JAVA));
    }

    private Project project(ProjectStatus status, List<Position> positions) {
        return Project.reconstitute(1L, 100L, "페어링 웹 리뉴얼", LocalDate.of(2026, 9, 1),
                false, 4, PeriodUnit.MONTH, 40_000_000L, WorkStyle.REMOTE, WorkForm.FULL_TIME,
                null, "상황", "담당 업무", "세부 범위", null,
                status, ProjectPaymentStatus.DEPOSIT_PAID,
                positions.stream().mapToInt(Position::getHeadcount).sum(),
                positions.stream().mapToInt(Position::getConfirmedCount).sum(),
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), 0, 0, 0,
                LocalDateTime.now(), null, null, null, LocalDateTime.now(),
                positions, List.of());
    }

    @Test
    @DisplayName("전원 서명이 끝나도 진행중으로 넘어가지 않는다")
    void signingAloneDoesNotStartProgress() {
        // 착수금 수수료 결제가 남아 있다. 상태는 계약 대기에 머문다.
        Project project = project(ProjectStatus.CONTRACT_PENDING, List.of(position(10L, 1, 0)));

        project.confirmPosition(10L);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.CONTRACT_PENDING);
        assertThat(project.isFullyStaffed()).isTrue();
    }

    @Test
    @DisplayName("인원이 다 차고 결제까지 끝나면 진행중이 된다")
    void startsProgressWhenFullyStaffed() {
        Project project = project(ProjectStatus.CONTRACT_PENDING, List.of(position(10L, 1, 1)));

        assertThat(project.startProgress()).isTrue();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("인원이 덜 찼으면 결제가 끝나도 진행중이 되지 않는다")
    void staysWhenNotFullyStaffed() {
        // 3명 중 2명만 계약한 상태에서 그 2명이 수수료를 내도 진행중이면 안 된다.
        Project project = project(ProjectStatus.CONTRACT_PENDING, List.of(position(10L, 3, 2)));

        assertThat(project.startProgress()).isFalse();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.CONTRACT_PENDING);
    }

    @Test
    @DisplayName("이미 진행중이면 다시 넘기지 않는다")
    void isIdempotent() {
        Project project = project(ProjectStatus.IN_PROGRESS, List.of(position(10L, 1, 1)));

        assertThat(project.startProgress()).isFalse();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("포지션이 여러 개면 전부 차야 진행중이 된다")
    void allPositionsMustBeFilled() {
        Project project = project(ProjectStatus.CONTRACT_PENDING,
                List.of(position(10L, 1, 1), position(11L, 2, 1)));

        assertThat(project.startProgress()).isFalse();

        project.confirmPosition(11L);

        assertThat(project.isFullyStaffed()).isTrue();
        assertThat(project.startProgress()).isTrue();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }
}
