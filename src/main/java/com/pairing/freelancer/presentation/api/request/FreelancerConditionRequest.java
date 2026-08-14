package com.pairing.freelancer.presentation.api.request;

import com.pairing.freelancer.application.command.UpsertConditionCommand;
import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.SkillLevel;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * 프리랜서 등록 조건. (요구사항 R21 화면 1)
 *
 * <p>매칭 조건의 기준값이다. 금액은 원 단위로 보내되 화면에서는 만원 단위로 입력받는다.
 */
@Schema(description = "프리랜서 조건 등록/수정 요청")
public record FreelancerConditionRequest(

        @Schema(description = "직군", example = "DEVELOPMENT")
        @NotNull(message = "직군은 필수입니다.")
        JobCategory jobCategory,

        @Schema(description = "직무", example = "BACKEND")
        @NotNull(message = "직무는 필수입니다.")
        JobRole jobRole,

        // 소속은 없앴다(2026-08-13). 페어링 프리랜서는 개인만 받기로 해서 항상 같은 값이 되고,
        // 항상 같은 값을 받는 입력칸은 사용자에게 물을 이유가 없다.

        @Schema(description = "근무 방식")
        @NotNull(message = "근무 방식은 필수입니다.")
        WorkStyle workStyle,

        @Schema(description = "근무 형태")
        @NotNull(message = "근무 형태는 필수입니다.")
        WorkForm workForm,

        @Schema(description = "희망 급여 단위")
        @NotNull(message = "급여 단위는 필수입니다.")
        PayUnit payUnit,

        @Schema(description = "희망 급여(원). 만원 단위로만 받는다. 최소 1만원", example = "5000000")
        @NotNull(message = "희망 급여는 필수입니다.")
        @Min(value = 10_000L, message = "최소 1만원 이상입니다.")
        Long payAmount,

        // 등록 화면에는 없는 항목이다. 실제 협상 하한선은 협상 시작 시 쟁점별로 따로 받는다.
        // (POST /api/v1/negotiations/{id}/start) 여기 값은 기본값 힌트로만 쓴다.
        @Schema(description = "최저 수용 금액(원). 만원 단위. 선택 항목이며 협상 하한선의 기본값 힌트로만 쓰인다.",
                example = "4000000")
        @Min(value = 10_000L, message = "최소 1만원 이상입니다.")
        Long minAcceptAmount,

        @Schema(description = "프로젝트 시작 가능일")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate availableFrom,

        @Schema(description = "시작일 협의 가능 여부", example = "true")
        boolean startNegotiable,

        @Schema(description = "희망 프로젝트 기간 값. 최대 24. 선택 입력(협의 가능이면 비워둘 수 있음)", example = "6")
        @Min(1) @Max(24)
        Integer periodValue,

        @Schema(description = "기간 단위")
        @NotNull(message = "기간 단위는 필수입니다.")
        PeriodUnit periodUnit,

        @Schema(description = "프리랜서 경험 여부", example = "true")
        @NotNull(message = "프리랜서 경험 여부는 필수입니다.")
        Boolean hasFreelanceExperience,

        @Schema(description = "경력(년). 최소 1년", example = "5")
        @Min(value = 1, message = "경력은 최소 1년입니다.")
        @Max(value = 50, message = "경력이 너무 큽니다.")
        int careerYears,

        @Schema(description = "보유 스킬. 1개 이상, 각 스킬마다 숙련도 선택")
        @NotEmpty(message = "보유 스킬은 1개 이상입니다.")
        @Valid
        List<Skill> skills
) {

    public UpsertConditionCommand toCommand(Long accountId) {
        return new UpsertConditionCommand(
                accountId, jobCategory, jobRole, workStyle, workForm, payUnit, payAmount,
                minAcceptAmount, availableFrom, startNegotiable, periodValue, periodUnit,
                hasFreelanceExperience, careerYears,
                skills.stream().map(skill -> new UpsertConditionCommand.Skill(skill.skillCode(), skill.skillLevel()))
                        .toList()
        );
    }

    @Schema(name = "ConditionSkillRequest", description = "보유 스킬")
    public record Skill(
            @Schema(description = "스킬", example = "JAVA")
            @NotNull(message = "스킬은 필수입니다.")
            SkillCode skillCode,

            @Schema(description = "숙련도", example = "ADVANCED")
            @NotNull(message = "숙련도는 필수입니다.")
            SkillLevel skillLevel
    ) {
    }
}
