package com.pairing.freelancer.domain.model;

import com.pairing.freelancer.exception.FreelancerErrorCode;
import com.pairing.global.exception.BusinessException;
import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 프리랜서 조건. 매칭의 기준값이다. (요구사항 R21 화면 1)
 *
 * <p>계정과 1:1이다. {@code PUT /me/condition} 은 없으면 생성하고 있으면 이 모델 전체를 덮어쓴다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FreelancerCondition {

    private Long id;
    private Long accountId;
    private JobCategory jobCategory;
    private JobRole jobRole;
    private String affiliation;
    private WorkStyle workStyle;
    private WorkForm workForm;
    private PayUnit payUnit;
    private Long payAmount;
    private Long minAcceptAmount;
    private LocalDate availableFrom;
    private boolean startNegotiable;
    private Integer periodValue;
    private PeriodUnit periodUnit;
    private boolean hasFreelanceExperience;
    private int careerYears;
    private List<ConditionSkill> skills;

    private FreelancerCondition(Long id, Long accountId, JobCategory jobCategory, JobRole jobRole,
                                String affiliation, WorkStyle workStyle, WorkForm workForm, PayUnit payUnit,
                                Long payAmount, Long minAcceptAmount, LocalDate availableFrom,
                                boolean startNegotiable, Integer periodValue, PeriodUnit periodUnit,
                                boolean hasFreelanceExperience, int careerYears, List<ConditionSkill> skills) {
        validate(accountId, jobCategory, jobRole, workStyle, workForm, payUnit, payAmount, periodUnit, skills);
        this.id = id;
        this.accountId = accountId;
        this.jobCategory = jobCategory;
        this.jobRole = jobRole;
        this.affiliation = affiliation;
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
        this.skills = skills;
    }

    public static FreelancerCondition create(Long accountId, JobCategory jobCategory, JobRole jobRole,
                                             String affiliation, WorkStyle workStyle, WorkForm workForm,
                                             PayUnit payUnit, Long payAmount, Long minAcceptAmount,
                                             LocalDate availableFrom, boolean startNegotiable,
                                             Integer periodValue, PeriodUnit periodUnit,
                                             boolean hasFreelanceExperience, int careerYears,
                                             List<ConditionSkill> skills) {
        return new FreelancerCondition(null, accountId, jobCategory, jobRole, affiliation, workStyle, workForm,
                payUnit, payAmount, minAcceptAmount, availableFrom, startNegotiable, periodValue, periodUnit,
                hasFreelanceExperience, careerYears, skills);
    }

    public static FreelancerCondition reconstitute(Long id, Long accountId, JobCategory jobCategory,
                                                   JobRole jobRole, String affiliation, WorkStyle workStyle,
                                                   WorkForm workForm, PayUnit payUnit, Long payAmount,
                                                   Long minAcceptAmount, LocalDate availableFrom,
                                                   boolean startNegotiable, Integer periodValue,
                                                   PeriodUnit periodUnit, boolean hasFreelanceExperience,
                                                   int careerYears, List<ConditionSkill> skills) {
        return new FreelancerCondition(id, accountId, jobCategory, jobRole, affiliation, workStyle, workForm,
                payUnit, payAmount, minAcceptAmount, availableFrom, startNegotiable, periodValue, periodUnit,
                hasFreelanceExperience, careerYears, skills);
    }

    /** {@code PUT /me/condition} 재호출. 기존 값을 전부 새 값으로 교체한다. */
    public void replaceWith(JobCategory jobCategory, JobRole jobRole, String affiliation, WorkStyle workStyle,
                            WorkForm workForm, PayUnit payUnit, Long payAmount, Long minAcceptAmount,
                            LocalDate availableFrom, boolean startNegotiable, Integer periodValue,
                            PeriodUnit periodUnit, boolean hasFreelanceExperience, int careerYears,
                            List<ConditionSkill> skills) {
        validate(this.accountId, jobCategory, jobRole, workStyle, workForm, payUnit, payAmount, periodUnit, skills);
        this.jobCategory = jobCategory;
        this.jobRole = jobRole;
        this.affiliation = affiliation;
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
        this.skills = skills;
    }

    private static void validate(Long accountId, JobCategory jobCategory, JobRole jobRole, WorkStyle workStyle,
                                 WorkForm workForm, PayUnit payUnit, Long payAmount, PeriodUnit periodUnit,
                                 List<ConditionSkill> skills) {
        if (accountId == null || jobCategory == null || jobRole == null || workStyle == null || workForm == null
                || payUnit == null || payAmount == null || periodUnit == null
                || skills == null || skills.isEmpty()) {
            throw new BusinessException(FreelancerErrorCode.INVALID_CONDITION_FIELD);
        }
    }
}
