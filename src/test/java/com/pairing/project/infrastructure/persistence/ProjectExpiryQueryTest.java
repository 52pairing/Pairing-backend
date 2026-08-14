package com.pairing.project.infrastructure.persistence;

import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.project.domain.model.PositionUpdate;
import com.pairing.project.domain.model.Project;
import com.pairing.project.domain.model.ProjectStatus;
import com.pairing.project.domain.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 만료 대상 조회가 <b>맞는 프로젝트만</b> 고르는지 DB 로 확인한다. (정책 P46)
 *
 * <p>도메인 규칙만 검증하면 이 종류를 못 잡는다. {@code expireRecruit} 이 아무리 정확해도
 * 조회가 엉뚱한 걸 물어오면 그 프로젝트가 취소된다. 반대로 조회가 좁으면 만료돼야 할 프로젝트가
 * 영원히 방치된다 — 상태를 {@code RECRUITING} 으로 한정했던 예전 쿼리가 정확히 그랬다.
 *
 * <p><b>취소는 되돌릴 수 없다.</b> 그래서 "잡아야 하는 것" 보다 "건드리면 안 되는 것" 을 더
 * 촘촘히 본다. 인원이 다 찬 프로젝트나 진행중인 프로젝트가 목록에 섞이면 계약까지 죽는다.
 */
@SpringBootTest
@Transactional
class ProjectExpiryQueryTest {

    private static final Long CLIENT_ID = 951_001L;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("모집중 · 마감 경과 · 인원 미달이면 잡힌다")
    void findsExpiredRecruiting() {
        Long id = saveExpired(ProjectStatus.RECRUITING, 2, 0);

        assertThat(expiredIds()).contains(id);
    }

    @Test
    @DisplayName("협상중도 잡힌다 — 상태로 거르면 여기가 통째로 빠진다")
    void findsExpiredNegotiating() {
        // 프리랜서가 한 명이라도 수락하면 프로젝트가 협상중으로 넘어간다.
        // 예전 쿼리는 RECRUITING 만 봐서 이 프로젝트를 영원히 놓쳤다.
        Long id = saveExpired(ProjectStatus.NEGOTIATING, 2, 0);

        assertThat(expiredIds()).contains(id);
    }

    @Test
    @DisplayName("계약 대기 · 일부만 확정이어도 잡힌다")
    void findsExpiredPartiallyContracted() {
        // 2명 모집에 1명만 서명을 마친 상태. 나머지 한 자리를 못 채웠다.
        Long id = saveExpired(ProjectStatus.CONTRACT_PENDING, 2, 1);

        assertThat(expiredIds()).contains(id);
    }

    @Test
    @DisplayName("인원이 다 찼으면 안 잡힌다")
    void skipsFullyStaffed() {
        // 착수금이 남았을 뿐 구할 사람은 다 구했다. 취소하면 계약까지 죽는다.
        Long id = saveExpired(ProjectStatus.CONTRACT_PENDING, 2, 2);

        assertThat(expiredIds()).doesNotContain(id);
    }

    @Test
    @DisplayName("마감 전이면 안 잡힌다")
    void skipsBeforeDeadline() {
        Long id = save(ProjectStatus.RECRUITING, 2, 0, LocalDateTime.now().plusDays(3));

        assertThat(expiredIds()).doesNotContain(id);
    }

    @Test
    @DisplayName("진행중은 안 잡힌다 — 인원 조건과 상태 조건 둘 다 막는다")
    void skipsInProgress() {
        Long id = saveExpired(ProjectStatus.IN_PROGRESS, 2, 2);

        assertThat(expiredIds()).doesNotContain(id);
    }

    @Test
    @DisplayName("이미 취소된 프로젝트는 다시 잡히지 않는다")
    void skipsAlreadyCanceled() {
        // 안 그러면 스케줄러가 돌 때마다 같은 프로젝트로 이벤트를 다시 쏜다.
        Long id = saveExpired(ProjectStatus.CANCELED, 2, 0);

        assertThat(expiredIds()).doesNotContain(id);
    }

    private List<Long> expiredIds() {
        entityManager.flush();
        entityManager.clear();
        return projectRepository.findExpiredUnderstaffed(LocalDateTime.now()).stream()
                .map(Project::getId)
                .toList();
    }

    private Long saveExpired(ProjectStatus status, int headcount, int confirmed) {
        return save(status, headcount, confirmed, LocalDateTime.now().minusDays(1));
    }

    /**
     * 원하는 상태·인원·마감일로 저장한다.
     *
     * <p>{@code Project.create} 로 만든 뒤 필요한 만큼 확정시켜 상태를 옮긴다. 상태 전이를
     * 도메인 메서드로 밟아야 저장 경로도 함께 검증된다.
     */
    private Long save(ProjectStatus status, int headcount, int confirmed, LocalDateTime deadline) {
        Project saved = projectRepository.save(project(headcount));
        Long positionId = saved.getPositions().get(0).getId();

        Project loaded = projectRepository.findById(saved.getId()).orElseThrow();
        for (int i = 0; i < confirmed; i++) {
            loaded.confirmPosition(positionId);
        }
        projectRepository.updateStateWithPositions(loaded);

        // 상태와 마감일은 도메인 경로로 못 만드는 조합이 있어 직접 맞춘다.
        entityManager.flush();
        entityManager.createQuery(
                        "UPDATE ProjectJpaEntity p SET p.status = :status, p.recruitDeadline = :deadline "
                                + "WHERE p.id = :id")
                .setParameter("status", status)
                .setParameter("deadline", deadline)
                .setParameter("id", saved.getId())
                .executeUpdate();

        return saved.getId();
    }

    private Project project(int headcount) {
        Project project = Project.create(CLIENT_ID, "모집 만료 조회 검증", LocalDate.now().plusDays(14),
                false, 4, PeriodUnit.MONTH, 40_000_000L,
                WorkStyle.REMOTE, WorkForm.FULL_TIME, null,
                "상황", "담당 업무", "세부 범위", null,
                List.of(new PositionUpdate(null, JobCategory.DEVELOPMENT, JobRole.BACKEND,
                        3, headcount, List.of(SkillCode.JAVA, SkillCode.SPRING_BOOT))),
                List.of());
        project.startRecruiting();
        return project;
    }
}
