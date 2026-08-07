package com.pairing.matching.presentation.api.request;

import com.pairing.matching.domain.model.RecommendationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 재추천 요청. (요구사항 R30)
 *
 * <p>FREE 는 요청한 후보가 전원 거절·만료된 경우에만, 프로젝트당 1회 가능하다.
 * PAID 는 최대 5회이며 1명당 10,000원이 부과된다.
 */
@Schema(description = "재추천 요청")
public record RerecommendRequest(

        @Schema(description = "재추천 종류", example = "PAID")
        @NotNull(message = "재추천 종류는 필수입니다.")
        RecommendationType type,

        @Schema(description = "추천받을 인원. PAID 일 때만 사용", example = "3")
        @Min(value = 1, message = "1명 이상이어야 합니다.")
        @Max(value = 20, message = "한 번에 20명까지 요청할 수 있습니다.")
        Integer quantity
) {
}
