package com.pairing.freelancer.presentation.api.response;

import com.pairing.freelancer.domain.model.FreelancerCondition;
import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.SkillLevel;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/** 프리랜서 조건. 매칭의 기준값이다. */
@Schema(description = "프리랜서 조건 응답")
public record FreelancerConditionResponse(

        @Schema(description = "조건 ID", example = "50") Long conditionId,
        @Schema(description = "직군") JobCategory jobCategory,
        @Schema(description = "직무") JobRole jobRole,
        @Schema(description = "소속") String affiliation,
        @Schema(description = "근무 방식") WorkStyle workStyle,
        @Schema(description = "근무 형태") WorkForm workForm,
        @Schema(description = "희망 급여 단위") PayUnit payUnit,
        @Schema(description = "희망 급여(원)", example = "5000000") Long payAmount,
        @Schema(description = "최저 수용 금액(원)", example = "4000000") Long minAcceptAmount,
        @Schema(description = "시작 가능일") LocalDate availableFrom,
        @Schema(description = "시작일 협의 가능 여부") boolean startNegotiable,
        @Schema(description = "희망 기간 값. 협의 가능이면 null", example = "6") Integer periodValue,
        @Schema(description = "기간 단위") PeriodUnit periodUnit,
        @Schema(description = "프리랜서 경험 여부") boolean hasFreelanceExperience,
        @Schema(description = "경력(년)", example = "5") int careerYears,
        @Schema(name = "ConditionSkillResponse", description = "보유 스킬") List<Skill> skills
) {

    public static FreelancerConditionResponse from(FreelancerCondition condition) {
        return new FreelancerConditionResponse(
                condition.getId(),
                condition.getJobCategory(),
                condition.getJobRole(),
                condition.getAffiliation(),
                condition.getWorkStyle(),
                condition.getWorkForm(),
                condition.getPayUnit(),
                condition.getPayAmount(),
                condition.getMinAcceptAmount(),
                condition.getAvailableFrom(),
                condition.isStartNegotiable(),
                condition.getPeriodValue(),
                condition.getPeriodUnit(),
                condition.isHasFreelanceExperience(),
                condition.getCareerYears(),
                condition.getSkills().stream()
                        .map(skill -> new Skill(skill.getSkillCode(), skill.getSkillLevel()))
                        .toList()
        );
    }

    @Schema(description = "보유 스킬")
    public record Skill(
            @Schema(description = "스킬") SkillCode skillCode,
            @Schema(description = "숙련도") SkillLevel skillLevel
    ) {
    }
}
