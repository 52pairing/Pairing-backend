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

    public void addSkill(PositionSkillJpaEntity skill) {
        skills.add(skill);
        skill.assignPosition(this);
    }
}