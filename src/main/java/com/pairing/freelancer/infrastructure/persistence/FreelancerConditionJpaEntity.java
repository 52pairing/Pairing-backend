package com.pairing.freelancer.infrastructure.persistence;

import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** freelancer_condition 테이블 매핑. account 와 1:1이다. */
@Entity
@Table(name = "freelancer_condition")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FreelancerConditionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, unique = true)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_category", nullable = false, length = 20)
    private JobCategory jobCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_role", nullable = false, length = 40)
    private JobRole jobRole;


    @Enumerated(EnumType.STRING)
    @Column(name = "work_style", nullable = false, length = 20)
    private WorkStyle workStyle;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_form", nullable = false, length = 20)
    private WorkForm workForm;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_unit", nullable = false, length = 20)
    private PayUnit payUnit;

    @Column(name = "pay_amount", nullable = false)
    private Long payAmount;

    @Column(name = "min_accept_amount")
    private Long minAcceptAmount;

    @Column(name = "available_from")
    private LocalDate availableFrom;

    @Column(name = "start_negotiable", nullable = false)
    private boolean startNegotiable;

    @Column(name = "period_value")
    private Integer periodValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_unit", nullable = false, length = 10)
    private PeriodUnit periodUnit;

    @Column(name = "has_freelance_experience", nullable = false)
    private boolean hasFreelanceExperience;

    @Column(name = "career_years", nullable = false)
    private int careerYears;

    // condition_skill 은 조건 저장 시 전체 교체된다. 개별 행 단위 CRUD 가 없어 컬렉션 매핑으로 충분하다.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "condition_skill", joinColumns = @JoinColumn(name = "condition_id"))
    private List<ConditionSkillEmbeddable> skills = new ArrayList<>();

    public FreelancerConditionJpaEntity(Long id, Long accountId, JobCategory jobCategory, JobRole jobRole,
                                        WorkStyle workStyle, WorkForm workForm,
                                        PayUnit payUnit, Long payAmount, Long minAcceptAmount,
                                        LocalDate availableFrom, boolean startNegotiable, Integer periodValue,
                                        PeriodUnit periodUnit, boolean hasFreelanceExperience, int careerYears,
                                        List<ConditionSkillEmbeddable> skills) {
        this.id = id;
        this.accountId = accountId;
        this.jobCategory = jobCategory;
        this.jobRole = jobRole;
        this.workStyle = workStyle;
        this.workForm = workForm;
        this.payUnit = payUnit;
        this.payAmount = payAmount;
        this.minAcceptAmount = minAcceptAmount;
        this.availableFrom = availableFrom;
        this.startNegotiable = startNegotiable;
        this.periodValue = periodValue;
        this.periodUnit = periodUnit;
        this.hasFreelanceExperience = hasFreelanceExperience;
        this.careerYears = careerYears;
        this.skills = skills == null ? new ArrayList<>() : skills;
    }
}
