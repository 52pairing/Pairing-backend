package com.pairing.project.domain.model;

import com.pairing.global.exception.BusinessException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 모집 만료·종료 판정. (정책 P46)
 *
 * <p>핵심은 <b>상태가 아니라 인원으로 판정한다</b>는 것이다. 프리랜서가 한 명이라도 수락하면
 * 프로젝트가 협상중으로 넘어가는데, 상태로 거르면 그 프로젝트는 마감이 지나도 영원히 잡히지
 * 않는다. 실제로 그렇게 구현돼 있었고 방치 구간이 생겼다.
 *
 * <p>반대로 <b>취소는 되돌릴 수 없다.</b> 인원이 찼거나 이미 진행 중인 프로젝트를 잘못
 * 취소하면 계약까지 죽는다. 그래서 "잡아야 하는 경우" 와 "건드리면 안 되는 경우" 를 함께 잠근다.
 */
class ProjectCancelRuleTest {

    @Test
    @DisplayName("모집중 · 인원 미달이면 만료된다")
    void expiresWhileRecruiting() {
        Project project = project(ProjectStatus.RECRUITING, position(10L, 2, 0));

        project.expireRecruit(retention());

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.CANCELED);
    }

    @Test
    @DisplayName("협상중이어도 인원이 미달이면 만료된다")
    void expiresWhileNegotiating() {
        // 수락 한 건에 협상중으로 넘어간다. 상태로 걸렀더니 여기가 통째로 빠져 있었다.
        Project project = project(ProjectStatus.NEGOTIATING, position(10L, 2, 0));

        project.expireRecruit(retention());

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.CANCELED);
    }

    @Test
    @DisplayName("계약 대기 · 일부만 확정이면 만료된다")
    void expiresWhilePartiallyContracted() {
        // 2명 모집에 1명만 서명을 마친 상태. 나머지 한 자리는 못 채웠다.
        Project project = project(ProjectStatus.CONTRACT_PENDING, position(10L, 2, 1));

        project.expireRecruit(retention());

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.CANCELED);
    }

    @Test
    @DisplayName("인원이 다 찼으면 만료되지 않는다")
    void doesNotExpireWhenFullyStaffed() {
        // 착수금이 남았을 뿐 구할 사람은 다 구했다. 그 상태는 P47 이 다룬다.
        Project project = project(ProjectStatus.CONTRACT_PENDING, position(10L, 2, 2));

        assertThatThrownBy(() -> project.expireRecruit(retention()))
                .isInstanceOf(BusinessException.class);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.CONTRACT_PENDING);
    }

    @Test
    @DisplayName("진행중은 되돌리지 않는다")
    void doesNotExpireInProgress() {
        // 일이 시작됐다. 중단은 중도 파기(P32)라 위약금 산정이 붙는 다른 절차다.
        Project project = project(ProjectStatus.IN_PROGRESS, position(10L, 2, 2));

        assertThatThrownBy(() -> project.expireRecruit(retention()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("미충원 포지션만 닫는다")
    void closesOnlyUnfilledPositions() {
        Project project = project(ProjectStatus.CONTRACT_PENDING,
                position(10L, 1, 1), position(11L, 2, 0));

        project.expireRecruit(retention());

        assertThat(position(project, 10L).getStatus()).isNotEqualTo(PositionStatus.CLOSED);
        assertThat(position(project, 11L).getStatus()).isEqualTo(PositionStatus.CLOSED);
    }

    @Test
    @DisplayName("모집 종료도 협상중 · 계약 대기에서 된다")
    void closeRecruitWorksAfterRecruiting() {
        // 상태로 막으면 수락 한 건 뒤로는 클라이언트가 프로젝트를 접을 방법이 없어진다.
        Project project = project(ProjectStatus.NEGOTIATING, position(10L, 2, 0));

        project.closeRecruit(retention());

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.CANCELED);
    }

    @Test
    @DisplayName("만료는 마감일을 덮어쓰지 않고, 모집 종료는 지금으로 당긴다")
    void keepsDeadlineOnExpiryOnly() {
        // 언제 만료됐는지가 파기 판정 근거다. 클라이언트가 직접 닫은 것과 갈리는 지점이기도 하다.
        LocalDateTime deadline = LocalDateTime.now().minusDays(3);

        Project expired = project(ProjectStatus.RECRUITING, deadline, position(10L, 2, 0));
        expired.expireRecruit(retention());
        assertThat(expired.getRecruitDeadline()).isEqualTo(deadline);

        Project closed = project(ProjectStatus.RECRUITING, deadline, position(10L, 2, 0));
        closed.closeRecruit(retention());
        assertThat(closed.getRecruitDeadline()).isAfter(deadline);
    }

    @Test
    @DisplayName("확정 인원이 포지션 합계와 다르면 불일치로 잡힌다")
    void detectsHeadcountDrift() {
        // 파생값이라 저장 경로가 빠뜨리면 조용히 어긋난다. 그대로 믿으면 다 찬 프로젝트가 취소된다.
        Project sound = project(ProjectStatus.CONTRACT_PENDING, position(10L, 2, 1));
        assertThat(sound.hasConsistentHeadcount()).isTrue();

        Project drifted = projectWithHeadcount(ProjectStatus.CONTRACT_PENDING, 0, position(10L, 2, 1));
        assertThat(drifted.hasConsistentHeadcount()).isFalse();
    }

    private Position position(long id, int headcount, int confirmedCount) {
        return Position.reconstitute(id, (int) id, JobCategory.DEVELOPMENT, JobRole.BACKEND,
                1, headcount, confirmedCount, PositionStatus.RECRUITING, null,
                List.of(SkillCode.JAVA));
    }

    private Position position(Project project, Long positionId) {
        return project.getPositions().stream()
                .filter(p -> positionId.equals(p.getId()))
                .findFirst()
                .orElseThrow();
    }

    private Project project(ProjectStatus status, Position... positions) {
        return project(status, LocalDateTime.now().minusDays(1), positions);
    }

    private Project project(ProjectStatus status, LocalDateTime deadline, Position... positions) {
        int confirmed = List.of(positions).stream().mapToInt(Position::getConfirmedCount).sum();
        return projectWithHeadcount(status, confirmed, deadline, positions);
    }

    private Project projectWithHeadcount(ProjectStatus status, int confirmedHeadcount, Position... positions) {
        return projectWithHeadcount(status, confirmedHeadcount, LocalDateTime.now().minusDays(1), positions);
    }

    /** 저장된 확정 인원을 포지션 합계와 다르게 줄 수 있어야 불일치 감지를 시험할 수 있다. */
    private Project projectWithHeadcount(ProjectStatus status, int confirmedHeadcount,
                                         LocalDateTime deadline, Position... positions) {
        List<Position> list = List.of(positions);
        return Project.reconstitute(1L, 100L, "페어링 웹 리뉴얼", LocalDate.of(2026, 9, 1),
                false, 4, PeriodUnit.MONTH, 40_000_000L, WorkStyle.REMOTE, WorkForm.FULL_TIME,
                null, "상황", "담당 업무", "세부 범위", null,
                status, ProjectPaymentStatus.DEPOSIT_PAID,
                list.stream().mapToInt(Position::getHeadcount).sum(),
                confirmedHeadcount,
                LocalDateTime.now().minusWeeks(3), deadline, 0, 0, 0,
                LocalDateTime.now(), null, null, null, LocalDateTime.now(),
                list, List.of());
    }

    private LocalDate retention() {
        return LocalDate.now().plusYears(5);
    }
}
