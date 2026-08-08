package com.pairing.project.presentation.api.request;

import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.SkillCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * 포지션 수정 1건.
 *
 * <p>등록용 {@link PositionRequest} 와 분리한 이유는 기존 포지션을 식별해야 하기 때문이다.
 * 모집 시작 후에는 project_position.id 를 참조하는 테이블이 7개라 삭제 후 재생성할 수 없다.
 *
 * <p>모집 인원과 포지션 추가·삭제는 착수금 결제 전(REGISTERED)까지만 가능하다. 검증은 서비스가 한다.
 */
@Schema(description = "프로젝트 포지션 수정 요청")
public record PositionUpdateRequest(

        @Schema(description = "포지션 ID. 새로 추가하는 포지션이면 null", example = "10")
        Long positionId,

        @Schema(description = "직군", example = "DEVELOPMENT")
        @NotNull(message = "직군은 필수입니다.")
        JobCategory jobCategory,

        @Schema(description = "직무", example = "BACKEND")
        @NotNull(message = "직무는 필수입니다.")
        JobRole jobRole,

        @Schema(description = "최소 경력(년)", example = "3")
        @Min(value = 1, message = "경력은 최소 1년입니다.")
        @Max(value = 50, message = "경력이 너무 큽니다.")
        int minCareerYears,

        @Schema(description = "모집 인원. 착수금 결제 후에는 변경할 수 없다.", example = "2")
        @Min(value = 1, message = "인원은 최소 1명입니다.")
        @Max(value = 50, message = "인원이 너무 많습니다.")
        int headcount,

        @Schema(description = "요구 스킬. 1개 이상", example = "[\"JAVA\", \"SPRING_BOOT\"]")
        @NotEmpty(message = "요구 스킬은 1개 이상입니다.")
        List<SkillCode> skills

) {
    }
