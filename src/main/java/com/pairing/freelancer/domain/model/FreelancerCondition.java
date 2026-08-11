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

    /** 희망 급여·최저 수용 금액의 입력 단위. 만원. */
    private static final long AMOUNT_UNIT = 10_000L;

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
        validate(accountId, jobCategory, jobRole, workStyle, workForm, payUnit, payAmount, minAcceptAmount,
                periodUnit, skills);
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
        validate(this.accountId, jobCategory, jobRole, workStyle, workForm, payUnit, payAmount, minAcceptAmount,
                periodUnit, skills);
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
                                 WorkForm workForm, PayUnit payUnit, Long payAmount, Long minAcceptAmount,
                                 PeriodUnit periodUnit, List<ConditionSkill> skills) {
        if (accountId == null || jobCategory == null || jobRole == null || workStyle == null || workForm == null
                || payUnit == null || payAmount == null || periodUnit == null
                || skills == null || skills.isEmpty()) {
            throw new BusinessException(FreelancerErrorCode.INVALID_CONDITION_FIELD);
        }
        requireInTenThousandUnit(payAmount);
        if (minAcceptAmount != null) {
            requireInTenThousandUnit(minAcceptAmount);
        }
    }

    /**
     * 금액은 만원 단위로만 받는다. 최소 1만원. (요구사항 명세)
     *
     * <p>저장은 원 단위 그대로다. 화면이 만원으로 보여주고 서버는 만원 배수인지만 본다.
     * 여기서 나누어 담으면 협상·계약·정산이 쓰는 금액 단위와 어긋난다.
     */
    private static void requireInTenThousandUnit(long amount) {
        if (amount < AMOUNT_UNIT || amount % AMOUNT_UNIT != 0) {
            throw new BusinessException(FreelancerErrorCode.INVALID_CONDITION_FIELD);
        }
    }
}
