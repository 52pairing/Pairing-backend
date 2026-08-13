package com.pairing.project.infrastructure.persistence;

import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.project.domain.model.PositionStatus;
import com.pairing.project.domain.model.Position;
import com.pairing.project.domain.model.PositionUpdate;
import com.pairing.project.domain.model.Project;
import com.pairing.project.domain.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약 체결로 올라간 확정 인원이 DB 까지 내려가는지 본다.
 *
 * <p>도메인만 검증하면 이 버그를 못 잡는다. {@code Position.confirm()} 은 메모리에서 잘 올라가는데
 * 저장 경로가 상태만 옮기고 {@code confirmedCount} 를 빠뜨리고 있었다. 그러면 포지션이
 * <b>CLOSED 로 닫혀 있는데 확정 인원은 0</b> 인 상태로 남아, 착수금을 다 내도
 * {@code Project.isFullyStaffed()} 가 거짓이라 프로젝트가 진행중으로 넘어가지 못한다.
 *
 * <p>그래서 저장하고 <b>다시 읽어서</b> 확인한다. 영속성 컨텍스트가 물고 있는 인스턴스를 그대로
 * 보면 메모리 값이라 통과해버리므로 {@code flush} 와 {@code clear} 를 끼운다.
 */
@SpringBootTest
@Transactional
class PositionConfirmPersistenceTest {

    private static final Long CLIENT_ID = 950_001L;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("계약 체결로 확정된 인원이 DB 에 저장된다")
    void persistsConfirmedCount() {
        Project saved = projectRepository.save(project(2));
        Long positionId = saved.getPositions().get(0).getId();

        Project loaded = projectRepository.findById(saved.getId()).orElseThrow();
        loaded.confirmPosition(positionId);
        projectRepository.updateStateWithPositions(loaded);

        assertThat(reload(saved.getId()).getPositions().get(0).getConfirmedCount())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("인원이 다 차면 확정 인원과 CLOSED 가 함께 저장된다 — 하나만 남으면 어긋난다")
    void persistsConfirmedCountWithClosedStatus() {
        Project saved = projectRepository.save(project(1));
        Long positionId = saved.getPositions().get(0).getId();

        Project loaded = projectRepository.findById(saved.getId()).orElseThrow();
        loaded.confirmPosition(positionId);
        projectRepository.updateStateWithPositions(loaded);

        Position reloaded = reload(saved.getId()).getPositions().get(0);
        assertThat(reloaded.getConfirmedCount()).isEqualTo(1);
        assertThat(reloaded.getStatus()).isEqualTo(PositionStatus.CLOSED);

        // 이게 곧 진행중 전환의 조건이다. 확정 인원이 안 내려가면 여기서 막힌다.
        assertThat(reload(saved.getId()).isFullyStaffed()).isTrue();
    }

    @Test
    @DisplayName("확정 인원과 무관한 저장은 기존 값을 덮어쓰지 않는다")
    void keepsConfirmedCountOnUnrelatedSave() {
        Project saved = projectRepository.save(project(3));
        Long positionId = saved.getPositions().get(0).getId();

        Project loaded = projectRepository.findById(saved.getId()).orElseThrow();
        loaded.confirmPosition(positionId);
        projectRepository.updateStateWithPositions(loaded);

        // 모집 종료처럼 상태만 바꾸는 경로. 읽어온 값을 그대로 다시 쓰므로 1 이 유지돼야 한다.
        Project again = reload(saved.getId());
        again.closeRecruit(LocalDate.now().plusYears(5));
        projectRepository.updateStateWithPositions(again);

        assertThat(reload(saved.getId()).getPositions().get(0).getConfirmedCount())
                .isEqualTo(1);
    }

    /** 영속성 컨텍스트를 비우고 DB 에서 새로 읽는다. */
    private Project reload(Long projectId) {
        entityManager.flush();
        entityManager.clear();
        return projectRepository.findById(projectId).orElseThrow();
    }

    private Project project(int headcount) {
        Project project = Project.create(CLIENT_ID, "확정 인원 저장 검증", LocalDate.now().plusDays(14),
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
