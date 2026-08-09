package com.pairing.project.presentation.api.request;

import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.SkillCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 모집 인원 1건(포지션).
 *
 * * <p>인원수(headcount)는 착수금 결제 후(RECRUITING 이후) 변경할 수 없다.
 *  * 매칭 요청·계약이 포지션 단위로 묶이기 때문이다.
 */
@Schema(description = "프로젝트 포지션 요청")
public record PositionRequest(

        @Schema(description = "직군", example = "DEVELOPMENT")
        @NotNull(message = "직군은 필수입니다.")
        JobCategory jobCategory,

        @Schema(description = "직무", example = "BACKEND")
        @NotNull(message = "직무는 필수입니다.")
        JobRole jobRole,

        @Schema(description = "최소 경력(년). 0년은 입력할 수 없다.", example = "3")
        @Min(value = 1, message = "경력은 최소 1년입니다.")
        @Max(value = 50, message = "경력이 너무 큽니다.")
        int minCareerYears,

        @Schema(description = "모집 인원", example = "2")
        @Min(value = 1, message = "인원은 최소 1명입니다.")
        @Max(value = 50, message = "인원이 너무 많습니다.")
        int headcount,

        @Schema(description = "요구 스킬. 1개 이상", example = "[\"JAVA\", \"SPRING_BOOT\"]")
        @NotEmpty(message = "요구 스킬은 1개 이상입니다.")
        @Size(max = 63, message = "요구 스킬은 최대 63개입니다.")
        List<SkillCode> skills

) {
}
