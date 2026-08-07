package com.pairing.freelancer.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 매칭 설정. (마이페이지 &gt; 매칭 설정)
 *
 * <p>추천 대상에서 잠시 빠지고 싶을 때 {@code matchingPaused} 를 켠다.
 * 이미 진행 중인 매칭·협상은 영향을 받지 않는다.
 */
@Schema(description = "매칭 설정 요청")
public record MatchingSettingsRequest(

        @Schema(description = "AI 매칭 활용 동의", example = "true")
        @NotNull(message = "AI 매칭 동의 여부는 필수입니다.")
        Boolean aiMatchingAgreed,

        @Schema(description = "매칭 일시 중지", example = "false")
        @NotNull(message = "매칭 중지 여부는 필수입니다.")
        Boolean matchingPaused
) {
}
