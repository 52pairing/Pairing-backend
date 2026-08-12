package com.pairing.project.infrastructure.persistence;

import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.project.domain.model.PositionStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * project_position 테이블 매핑.
 *
 * <p>preferred_note 는 매핑하지 않는다. 우대사항은 프로젝트 단위(extra_note)로 통일했다.
 * created_at / updated_at 은 DB 기본값과 트리거가 채운다.
 */
@Entity
@Table(name = "project_position")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectPositionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectJpaEntity project;

    @Column(name = "position_no", nullable = false)
    private int positionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_category", nullable = false, length = 30)
    private JobCategory jobCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_role", nullable = false, length = 40)
    private JobRole jobRole;

    @Column(name = "min_career_years", nullable = false)
    private int minCareerYears;

    @Column(name = "headcount", nullable = false)
    private int headcount;

    @Column(name = "confirmed_count", nullable = false)
    private int confirmedCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PositionStatus status;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @OneToMany(mappedBy = "position", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<PositionSkillJpaEntity> skills = new ArrayList<>();

    public ProjectPositionJpaEntity(Long id, int positionNo, JobCategory jobCategory, JobRole jobRole,
                                    int minCareerYears, int headcount, int confirmedCount,
                                    PositionStatus status, LocalDateTime closedAt) {
        this.id = id;
        this.positionNo = positionNo;
        this.jobCategory = jobCategory;
        this.jobRole = jobRole;
        this.minCareerYears = minCareerYears;
        this.headcount = headcount;
        this.confirmedCount = confirmedCount;
        this.status = status;
        this.closedAt = closedAt;
    }

    void assignProject(ProjectJpaEntity project) {
        this.project = project;
    }

    /**
     * 수정된 조건을 반영한다. id 와 confirmedCount 는 건드리지 않는다.
     *
     * <p>매칭·협상·계약이 이 행의 id 를 참조하므로 삭제 후 재생성하면 안 된다.
     */
    public void applyCondition(int positionNo, JobCategory jobCategory, JobRole jobRole,
                               int minCareerYears, int headcount) {
        this.positionNo = positionNo;
        this.jobCategory = jobCategory;
        this.jobRole = jobRole;
        this.minCareerYears = minCareerYears;
        this.headcount = headcount;
    }

    /**
     * 확정 인원과 모집 마감 결과를 반영한다. 조건({@code applyCondition})은 건드리지 않는다.
     *
     * <p>{@code confirmedCount} 를 빠뜨리면 계약 체결이 DB 에 남지 않는다. 도메인이 메모리에서만
     * 올리고 상태({@code CLOSED})만 저장돼, <b>인원이 다 찬 것처럼 닫혀 있는데 확정 인원은 0</b> 인
     * 상태가 된다. 그러면 {@code Project.isFullyStaffed()} 가 거짓이라 착수금을 다 내도 프로젝트가
     * 진행중으로 넘어가지 못한다.
     */
    public void applyState(int confirmedCount, PositionStatus status, LocalDateTime closedAt) {
        this.confirmedCount = confirmedCount;
        this.status = status;
        this.closedAt = closedAt;
    }

    /**
     * 요구 스킬을 통째로 교체한다.
     *
     * <p>{@code uk_position_skill (position_id, skill_code)} 이 있어 기존 행이 남은 채로 넣으면
     * 중복 키가 난다. 비운 뒤 다시 채우고, 삭제가 먼저 나가도록 호출부가 flush 를 맞춘다.
     *
     * <p>position_skill 은 참조하는 테이블이 없어 전량 교체해도 안전하다.
     */
    public void clearSkills() {
        this.skills.clear();
    }

    public void addSkill(PositionSkillJpaEntity skill) {
        skills.add(skill);
        skill.assignPosition(this);
    }
}