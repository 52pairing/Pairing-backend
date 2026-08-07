package com.pairing.project.presentation.api.request;

import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.SkillCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 사전 검수 요청. (요구사항 R28, R30 / 프로젝트 등록 5단계)
 *
 * <p>등록 전이라 projectId 가 없으므로 입력 중인 포지션 조건만 보낸다.
 * 집계는 직무와 요구 스킬로만 하므로 경력·예산·기본 정보는 보내지 않는다.
 */
@Schema(description = "사전 검수 요청")
public record ProjectPreReviewRequest(

        @Schema(description = "확인할 포지션 조건 목록")
        @NotEmpty(message = "포지션 조건은 최소 1건입니다.")
        @Valid
        List<PreReviewPosition> positions
) {

        /**
         * 검수용 포지션 조건.
         *
         * <p>{@link PositionRequest} 와 분리한 이유는 검수가 직무·요구 스킬만 사용하기 때문이다.
         * 경력·우대사항까지 필수로 받으면 화면이 아직 채우지 않은 값을 강제하게 된다.
         */
        @Schema(description = "검수할 포지션 조건")
        public record PreReviewPosition(

                @Schema(description = "직무. 직군은 직무에서 유도한다.", example = "BACKEND")
                @NotNull(message = "직무는 필수입니다.")
                JobRole jobRole,

                @Schema(description = "모집 인원. 예상 후보 수와 비교하는 기준", example = "2")
                @Min(value = 1, message = "인원은 최소 1명입니다.")
                @Max(value = 50, message = "인원이 너무 많습니다.")
                int headcount,

                @Schema(description = "요구 스킬. 1개 이상", example = "[\"JAVA\", \"SPRING_BOOT\"]")
                @NotEmpty(message = "요구 스킬은 1개 이상입니다.")
                List<SkillCode> skills
        ) {
        }
}